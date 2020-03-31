package com.atlassian.braid;

import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

/**
 * Provides a default implementation for scalar coersion. This is used to provide a runtime wiring for any custom
 * scalars in stitched schemas that are not explicitly defined by the stitching service.
 * See {@link com.atlassian.braid.BraidSchema#wireScalarDefinitions} for usage
 */
public final class DefaultScalarCoercing implements Coercing<Object, Object> {

    @Override
    public Object serialize(Object input) throws CoercingSerializeException {
        return input;
    }

    @Override
    public Object parseValue(Object input) throws CoercingParseValueException {
        return input;
    }

    @Override
    public Object parseLiteral(Object input) throws CoercingParseLiteralException {
        return parseLiteral(input, emptyMap());
    }

    @Override
    public Object parseLiteral(Object input, Map<String, Object> variables) throws CoercingParseLiteralException {
        if (!(input instanceof Value)) {
            throw new CoercingParseLiteralException("Expected AST type 'StringValue' but was '" + (input) + "'.");
        }
        if (input instanceof NullValue) {
            return null;
        }
        if (input instanceof FloatValue) {
            return ((FloatValue) input).getValue();
        }
        if (input instanceof StringValue) {
            return ((StringValue) input).getValue();
        }
        if (input instanceof IntValue) {
            return ((IntValue) input).getValue();
        }
        if (input instanceof BooleanValue) {
            return ((BooleanValue) input).isValue();
        }
        if (input instanceof EnumValue) {
            return ((EnumValue) input).getName();
        }
        if (input instanceof VariableReference) {
            String varName = ((VariableReference) input).getName();
            return variables.get(varName);
        }
        if (input instanceof ArrayValue) {
            List<Value> values = ((ArrayValue) input).getValues();
            return values.stream()
                    .map(v -> parseLiteral(v, variables))
                    .collect(toList());
        }
        if (input instanceof ObjectValue) {
            List<ObjectField> values = ((ObjectValue) input).getObjectFields();
            Map<String, Object> parsedValues = new LinkedHashMap<>();
            values.forEach(fld -> {
                Object parsedValue = parseLiteral(fld.getValue(), variables);
                parsedValues.put(fld.getName(), parsedValue);
            });
            return parsedValues;
        }
        throw new CoercingParseLiteralException("Unable to coerce scalar value");
    }
}
