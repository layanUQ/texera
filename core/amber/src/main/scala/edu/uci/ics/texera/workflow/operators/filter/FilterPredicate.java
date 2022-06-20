package edu.uci.ics.texera.workflow.operators.filter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaInject;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaString;
import edu.uci.ics.texera.workflow.common.WorkflowContext;
import edu.uci.ics.texera.workflow.common.metadata.annotations.AutofillAttributeName;
import edu.uci.ics.texera.workflow.common.metadata.annotations.HideAnnotation;
import edu.uci.ics.texera.workflow.common.tuple.Tuple;
import edu.uci.ics.texera.workflow.common.tuple.schema.AttributeType;
import edu.uci.ics.texera.workflow.common.tuple.schema.AttributeTypeUtils;

import java.sql.Timestamp;
import java.util.Objects;

public class FilterPredicate {

    @JsonProperty(value = "attribute", required = true)
    @AutofillAttributeName
    public String attribute;

    @JsonProperty(value = "condition", required = true)
    public ComparisonType condition;

    @JsonSchemaInject(strings = {
            @JsonSchemaString(path = HideAnnotation.hideTarget, value = "condition"),
            @JsonSchemaString(path = HideAnnotation.hideType, value = HideAnnotation.Type.regex),
            @JsonSchemaString(path = HideAnnotation.hideExpectedValue, value = "is null|is not null")
    })
    @JsonProperty(value = "value")
    public String value;

    public FilterPredicate(String attribute, ComparisonType condition, String value) {
        this.attribute = attribute;
        this.condition = condition;
        this.value = value;
    }

    @JsonIgnore
    public boolean evaluate(Tuple tuple, WorkflowContext context) {
        boolean isFieldNull = tuple.getField(attribute) == null;
        if (condition == ComparisonType.IS_NULL) {
            return isFieldNull;
        } else if (condition == ComparisonType.IS_NOT_NULL) {
            return !isFieldNull;
        } else if (isFieldNull) {
            return false;
        }

        AttributeType type = tuple.getSchema().getAttribute(this.attribute).getType();
        switch (type) {
            case STRING:
            case ANY:
                return evaluateFilterString(tuple);
            case BOOLEAN:
                return evaluateFilterBoolean(tuple);
            case LONG:
                return evaluateFilterLong(tuple);
            case INTEGER:
                return evaluateFilterInt(tuple);
            case DOUBLE:
                return evaluateFilterDouble(tuple);
            case TIMESTAMP:
                return evaluateFilterTimestamp(tuple);

            default:
                throw new RuntimeException("unsupported attribute type: " + type);
        }
    }

    private boolean evaluateFilterBoolean(Tuple inputTuple) {
        Boolean tupleValue = inputTuple.getField(attribute, Boolean.class);
        return evaluateFilter(tupleValue.toString().toLowerCase(), value.trim().toLowerCase(), condition);
    }

    private boolean evaluateFilterDouble(Tuple inputTuple) {
        Double tupleValue = inputTuple.getField(attribute, Double.class);
        Double compareToValue = Double.parseDouble(value);
        return evaluateFilter(tupleValue, compareToValue, condition);
    }

    private boolean evaluateFilterInt(Tuple inputTuple) {
        Integer tupleValueInt = inputTuple.getField(attribute, Integer.class);
        Double tupleValueDouble = tupleValueInt == null ? null : (double) tupleValueInt;
        Double compareToValue = Double.parseDouble(value);
        return evaluateFilter(tupleValueDouble, compareToValue, condition);
    }

    private boolean evaluateFilterLong(Tuple inputTuple) {
        Long tupleValue = inputTuple.getField(attribute, Long.class);
        Long compareToValue = Long.valueOf(value.trim());
        return evaluateFilter(tupleValue, compareToValue, condition);
    }

    private boolean evaluateFilterString(Tuple inputTuple) {
        String tupleValue = inputTuple.getField(attribute).toString();
        try {
            Double tupleValueDouble = tupleValue == null ? null : Double.parseDouble(tupleValue.trim());
            Double compareToValueDouble = Double.parseDouble(value);
            return evaluateFilter(tupleValueDouble, compareToValueDouble, condition);
        } catch (NumberFormatException e) {
            return evaluateFilter(tupleValue, value, condition);
        }
    }

    private boolean evaluateFilterTimestamp(Tuple inputTuple) {
        Long tupleValue = inputTuple.getField(attribute, Timestamp.class).getTime();
        Long compareToValue = AttributeTypeUtils.parseTimestamp(value.trim()).getTime();
        return evaluateFilter(tupleValue, compareToValue, condition);

    }


    private static <T extends Comparable<T>> boolean evaluateFilter(T tupleValue, T userSuppliedValue, ComparisonType comparisonType) {
        int compareResult = tupleValue.compareTo(userSuppliedValue);
        switch (comparisonType) {
            case EQUAL_TO:
                return compareResult == 0;
            case GREATER_THAN:
                return compareResult > 0;
            case GREATER_THAN_OR_EQUAL_TO:
                return compareResult >= 0;
            case LESS_THAN:
                return compareResult < 0;
            case LESS_THAN_OR_EQUAL_TO:
                return compareResult <= 0;
            case NOT_EQUAL_TO:
                return compareResult != 0;
            default:
                throw new RuntimeException(
                        "Unable to do comparison: unknown comparison type: " + comparisonType);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FilterPredicate that = (FilterPredicate) o;
        return Objects.equals(attribute, that.attribute) && condition == that.condition
                && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attribute, condition, value);
    }
}
