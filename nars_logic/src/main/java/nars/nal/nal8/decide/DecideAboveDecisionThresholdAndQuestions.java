package nars.nal.nal8.decide;

import nars.Symbols;
import nars.concept.Concept;
import nars.nal.nal8.Operation;

/**
 * Created by me on 5/20/15.
 */
public class DecideAboveDecisionThresholdAndQuestions extends DecideAboveDecisionThreshold {

    public final static DecideAboveDecisionThresholdAndQuestions the = new DecideAboveDecisionThresholdAndQuestions();

    @Override
    public boolean decide(Concept c, Operation task) {
        if (task.getTask().getPunctuation() == Symbols.QUESTION)
            return true;

        return super.decide(c, task);
    }


}
