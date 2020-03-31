package com.atlassian.braid;

import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.atlassian.braid.TypeUtils.findQueryType;
import static com.atlassian.braid.Util.parseRegistry;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class TypeUtilsTest {

    @Test
    public void filterQueryType() throws IOException {
        TypeDefinitionRegistry registry = parseRegistry("/com/atlassian/braid/not-null-fields.graphql");
        TypeUtils.filterQueryType(registry, emptyList(), "foo");

        Optional<ObjectTypeDefinition> queryType = findQueryType(registry);
        assertThat(queryType).isPresent();

        final List<FieldDefinition> queryFieldDefinitions = queryType
                .map(ObjectTypeDefinition::getFieldDefinitions)
                .orElseThrow(IllegalStateException::new);

        assertThat(queryFieldDefinitions).hasSize(1);
        assertThat(queryFieldDefinitions).extracting("name").containsExactly("foo");
    }

    @Test
    public void filterQueryTypeButKeepAllFields() throws IOException {
        TypeDefinitionRegistry registry = parseRegistry("/com/atlassian/braid/not-null-fields.graphql");
        TypeUtils.filterQueryType(registry, emptyList());

        Optional<ObjectTypeDefinition> queryType = findQueryType(registry);
        assertThat(queryType).isPresent();

        final List<FieldDefinition> queryFieldDefinitions = queryType
                .map(ObjectTypeDefinition::getFieldDefinitions)
                .orElseThrow(IllegalStateException::new);

        assertThat(queryFieldDefinitions).hasSize(2);
    }

    @Test
    public void filterQueryTypeButKeepLinkReplacedFields() throws IOException {
        TypeDefinitionRegistry registry = parseRegistry("/com/atlassian/braid/not-null-fields.graphql");
        List<Link> links = singletonList(Link.from(SchemaNamespace.of("blah"), "Blah", "baz", "bar")
                .replaceFromField()
                .to(SchemaNamespace.of("blah"), "Blah", "nothing")
                .build());
        TypeUtils.filterQueryType(registry, links, "foo", "baz");

        Optional<ObjectTypeDefinition> queryType = findQueryType(registry);
        assertThat(queryType).isPresent();

        final List<FieldDefinition> queryFieldDefinitions = queryType
                .map(ObjectTypeDefinition::getFieldDefinitions)
                .orElseThrow(IllegalStateException::new);

        assertThat(queryFieldDefinitions.stream().map(FieldDefinition::getName).collect(Collectors.toList())).isEqualTo(
                asList("foo", "bar"));
    }
}
