package nars.term.transform;

import nars.Symbols;
import nars.term.Term;
import nars.term.Variable;

/**
 * Created by me on 6/2/15.
 */
public class TransformIndependentToDependentVariables extends VariableSubstitution {
    int counter = 0;

    @Override
    public boolean test(Term possiblyAVariable) {
        if (super.test(possiblyAVariable))
            return possiblyAVariable.hasVarIndep();
        return false;
    }

    @Override
    protected Variable getSubstitute(Variable v) {
        return Variable.the(Symbols.VAR_DEPENDENT, counter++);
    }
}
