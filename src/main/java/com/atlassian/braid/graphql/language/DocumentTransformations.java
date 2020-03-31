package com.atlassian.braid.graphql.language;

import com.atlassian.braid.FieldTransformationContext;
import com.atlassian.braid.transformation.BraidSchemaSource;
import graphql.language.Document;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeName;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;

import java.util.Set;

import static java.util.stream.Collectors.toSet;


public class DocumentTransformations {

    public static Document removeMissingFieldsIfBraidAndSourceTypeFieldsDiffer(FieldTransformationContext context, Document document,
                                                                               GraphQLOutputType fieldOutputType) {
        BraidSchemaSource ds = new BraidSchemaSource(context.getSchemaSource());
        return ds.getSchemaSource().getPrivateSchema().getType(ds.getSourceTypeName(fieldOutputType.getName()))

                .map(type -> {
                    if (fieldOutputType instanceof GraphQLObjectType && type instanceof ObjectTypeDefinition) {
                        Set<String> braidFieldNames = ((GraphQLObjectType) fieldOutputType).getFieldDefinitions().stream()
                                .map(GraphQLFieldDefinition::getName)
                                .collect(toSet());
                        Set<String> sourceFieldNames = ((ObjectTypeDefinition) type).getFieldDefinitions().stream().map(FieldDefinition::getName).collect(toSet());
                        if (!sourceFieldNames.equals(braidFieldNames)) {
                            RemoveUnknownFields removeUnknownFields = new RemoveUnknownFields(
                                    context.getSchemaSource().getPrivateSchema(),
                                    sourceFieldNames,
                                    type.getName(),
                                    document);

                            context.addMissingFields(removeUnknownFields.getFields());
                            return removeUnknownFields.getDocument();
                        }
                    }
                    return document;
                })
                .orElse(document);
    }

    public static Document renameTypesToSourceNames(BraidSchemaSource braidSchemaSource, Document document) {

        NodeTransformer transformer = new NodeTransformer() {
            @Override
            public TypeName typeName(TypeName node) {
                TypeName newTypeName = super.typeName(node);
                return newTypeName.transform(b ->
                        b.name(braidSchemaSource.getSourceTypeName(node.getName())));
            }
        };
        return transformer.document(document);
    }
}
