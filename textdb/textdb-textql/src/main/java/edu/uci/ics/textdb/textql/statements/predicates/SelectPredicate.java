package edu.uci.ics.textdb.textql.statements.predicates;

import edu.uci.ics.textdb.web.request.beans.OperatorBean;

/**
 * Object representation of a "SELECT (...)" predicate inside a { @code SelectExtractStatement }.
 * Subclasses have specific fields related to its projection functionalities.
 * SelectPredicate --+ SelectAllFieldsPredicate
 *                   + SelectSomeFieldsPredicate
 * 
 * @author Flavio Bayer
 *
 */
public abstract class SelectPredicate {

    /**
     * Return this operator converted to an { @code OperatorBean }.
     * @param selectOperatorId The ID of the OperatorBean to be created.
     */
    public abstract OperatorBean getOperatorBean(String selectOperatorId);
    

    @Override
    public boolean equals(Object other) {
        if (other == null) { return false; }
        if (other.getClass() != getClass()) { return false; }
        SelectPredicate selectPredicate = (SelectPredicate) other;
        return true;
    }
}
