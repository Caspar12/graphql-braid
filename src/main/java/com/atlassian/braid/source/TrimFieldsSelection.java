package com.atlassian.braid.source;

import com.atlassian.braid.Link;
import com.atlassian.braid.LinkArgument;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.transformation.BraidSchemaSource;
import graphql.analysis.QueryTraversal;
import graphql.analysis.QueryVisitor;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.Node;
import graphql.language.NodeTraverser;
import graphql.language.NodeVisitorStub;
import graphql.language.SelectionSet;
import graphql.language.SelectionSetContainer;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.atlassian.braid.LinkArgument.ArgumentSource.OBJECT_FIELD;
import static graphql.analysis.QueryTraversal.newQueryTraversal;
import static java.lang.String.format;

public class TrimFieldsSelection {


    public static List<FragmentDefinition> trimFieldSelection(SchemaSource schemaSource,
                                                              DataFetchingEnvironment environment,
                                                              Node root,
                                                              boolean ignoreFirstField) {
        List<FieldWithLink> fieldWithLinks = new ArrayList<>();
        BraidSchemaSource braidSchemaSource = new BraidSchemaSource(schemaSource);

        QueryVisitor nodeVisitor = new QueryVisitorStub() {

            @Override
            public void visitField(QueryVisitorFieldEnvironment env) {
                if (env.isTypeNameIntrospectionField()) {
                    return;
                }
                GraphQLFieldsContainer parentFieldsContainer = env.getFieldsContainer();
                Field field = env.getField();
                boolean isFirstField = env.getParentEnvironment() == null;
                if (isFirstField && ignoreFirstField) {
                    return;
                }
                Optional<Link> linkForField = getLinkForField(braidSchemaSource, schemaSource.getLinks(),
                        parentFieldsContainer.getName(), field.getName());

                linkForField.ifPresent(link -> {
                            SelectionSet parentSelectionSet = Optional.ofNullable(env.getSelectionSetContainer())
                                    .map(SelectionSetContainer::getSelectionSet)
                                    .orElse(null);
                            fieldWithLinks.add(new FieldWithLink(field, link, parentSelectionSet));
                        }
                );
            }
        };

        Map<String, FragmentDefinition> fragmentsByName = environment.getFragmentsByName().entrySet()
                .stream().collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().deepCopy()));


        QueryTraversal queryTraversal = newQueryTraversal()
                .schema(environment.getGraphQLSchema())
                .root(root)
                .rootParentType((GraphQLObjectType) environment.getParentType())
                .fragmentsByName(fragmentsByName)
                .variables(environment.getExecutionContext().getVariables()).build();
        queryTraversal.visitPreOrder(nodeVisitor);

        fieldWithLinks.forEach(TrimFieldsSelection::addLinkArgumentsToSourceSelections);

        Set<FragmentDefinition> referencedFragments = new LinkedHashSet<>();
        getReferencedFragments(root, fragmentsByName, referencedFragments);
        return new ArrayList<>(referencedFragments);
    }

    /**
     * Add fields to source selection set (if missing) that are needed as an arguments for a link. Original field for link will be
     * removed.
     *
     * @param fieldWithLink original field that was associated with the link.
     */
    private static void addLinkArgumentsToSourceSelections(FieldWithLink fieldWithLink) {
        SelectionSet parentSelectionSet = fieldWithLink.parentSelectionSet;
        if (parentSelectionSet == null) {
            // This is a special case where the link field is a top level field and we cannot
            // change the selection set (only field itself). In that case link must have exactly
            // one argument sourced from source object. In order to fix it more extensive Braid changes will be needed,
            // so that trimFieldSelection will be able to return SelectionSet with 0 or more fields.
            // This can be fixed once braid is refactored to use immutable graphql-java version.
            LinkArgument linkArgument = fieldWithLink.link.getLinkArguments().stream()
                    .filter(argument -> argument.getArgumentSource() == OBJECT_FIELD)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(format("Link for top level field '%s' requires exactly one object field argument",
                            fieldWithLink.link.getNewFieldName())));
            fieldWithLink.field.setSelectionSet(null);
            fieldWithLink.field.setName(linkArgument.getSourceName());
            return;
        }
        parentSelectionSet.getSelections().remove(fieldWithLink.field);
        fieldWithLink.link.getLinkArguments().stream()
                //We are interested in arguments that need to be taken from source object
                .filter(argument -> argument.getArgumentSource() == OBJECT_FIELD)
                .forEach(argument -> {
                    Field newField = Field.newField()
                            .name(argument.getSourceName())
                            .build();

                    if (!selectionSetContainsField(parentSelectionSet, newField)) {
                        parentSelectionSet.getSelections().add(newField);
                    }
                });
    }

    private static boolean selectionSetContainsField(SelectionSet selectionSet, Field fieldToCheck) {
        return selectionSet.getSelections().stream()
                .filter(selection -> selection instanceof Field)
                .map(field -> (Field) field)
                .anyMatch(field -> field.getName().equals(fieldToCheck.getName())
                        && Objects.equals(field.getAlias(), fieldToCheck.getAlias()));
    }

    /**
     * Recursively searches for fragments starting from the given root node
     *
     * @param root                  - The node to look for references in
     * @param fragmentDefinitionMap - the map of defined fragments in the query keyed by name
     * @param referencedFragments   - The set of already known referenced fragments
     */
    private static void getReferencedFragments(Node root,
                                               Map<String, FragmentDefinition> fragmentDefinitionMap,
                                               Set<FragmentDefinition> referencedFragments) {
        Set<FragmentDefinition> childFragments = new LinkedHashSet<>();
        NodeVisitorStub nodeVisitorStub = new NodeVisitorStub() {
            @Override
            public TraversalControl visitFragmentSpread(FragmentSpread fragmentSpread, TraverserContext<Node> context) {
                childFragments.add(fragmentDefinitionMap.get(fragmentSpread.getName()));
                return TraversalControl.CONTINUE;
            }
        };
        new NodeTraverser().preOrder(nodeVisitorStub, root);
        childFragments.stream()
                .filter(referencedFragments::add)
                .forEach(frag -> getReferencedFragments(frag, fragmentDefinitionMap, referencedFragments));
    }

    private static Optional<Link> getLinkForField(BraidSchemaSource braidSchemaSource,
                                                  Collection<Link> links,
                                                  String typeName,
                                                  String fieldName) {
        return links.stream()
                .filter(l -> braidSchemaSource.getLinkBraidSourceType(l).equals(typeName)
                        && l.getNewFieldName().equals(fieldName))
                .findFirst();
    }


    private static class FieldWithLink {
        public final Field field;
        public final Link link;
        public final SelectionSet parentSelectionSet;

        public FieldWithLink(Field field, Link link, SelectionSet parentSelectionSet) {
            this.field = field;
            this.link = link;
            this.parentSelectionSet = parentSelectionSet;
        }
    }


}
