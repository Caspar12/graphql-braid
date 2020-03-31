package com.atlassian.braid.graphql.language;

import graphql.language.Document;
import graphql.language.Field;
import graphql.language.Node;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.TypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.validation.DocumentVisitor;
import graphql.validation.LanguageTraversal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;

/**
 * Removes unknown fields for a specific type
 */
public class RemoveUnknownFields implements DocumentVisitor {
    private final DefinitionTraversalContext traversalContext;
    private final String sourceTypeName;
    private final Map<String, Field> removedFields = new HashMap<>();
    private final Node node;
    private final Set<String> originalTypeFieldNames;

    private final List<Field> fieldsToRemove = new ArrayList<>();
    private final Document document;

    RemoveUnknownFields(TypeDefinitionRegistry sourceSchema,
                               Set<String> originalTypeFieldNames,
                               String sourceTypeName,
                               Document node) {
        this.traversalContext = new DefinitionTraversalContext(sourceSchema);
        this.sourceTypeName = sourceTypeName;
        this.originalTypeFieldNames = originalTypeFieldNames;
        this.node = node;
        this.document = execute(node);
    }

    private Document execute(Document node) {
        LanguageTraversal languageTraversal = new LanguageTraversal();
        languageTraversal.traverse(this.node, this);

        fieldsToRemove.forEach(f -> removedFields.put(f.getName(), f));

        NodeTransformer transformer = new NodeTransformer() {
            @Override
            public SelectionSet selectionSet(SelectionSet node) {
                if (node == null) {
                    return null;
                }
                List<Selection> selections = node.getSelections().stream()
                        .filter(s -> fieldsToRemove.stream().noneMatch(f -> s.hashCode() == f.hashCode()))
                        .collect(toList());

                if (selections.isEmpty()) {
                    return null;
                } else {
                    return node.transform(b ->
                            b.selections(selections.stream()
                                .map(this::selection)
                                .collect(toList())));
                }
            }
        };
        return transformer.document(node);
    }

    List<Field> getFields() {
        return new ArrayList<>(removedFields.values());
    }

    Document getDocument() {
        return document;
    }

    @Override
    public void enter(Node node, List<Node> path) {
        traversalContext.enter(node, path);
        if (node instanceof Field) {
            checkField(traversalContext.getParentType(), path.isEmpty() ? Optional.empty() : Optional.of((SelectionSet) path.get(path.size() - 1)));
        }
    }

    private void checkField(TypeDefinition parentType, Optional<SelectionSet> parent) {
        if (parentType instanceof ObjectTypeDefinition && parentType.getName().equals(sourceTypeName)) {
            parent.ifPresent(selectionSet -> selectionSet.getSelections().stream()
                    .filter(s -> s instanceof Field)
                    .filter(s -> !originalTypeFieldNames.contains(((Field) s).getName()))
                    .filter(s -> !((Field) s).getName().startsWith("__"))
                    .forEach(s -> fieldsToRemove.add((Field) s)));
        }
    }

    @Override
    public void leave(Node node, List<Node> path) {
        traversalContext.leave(node, path);
    }
}
