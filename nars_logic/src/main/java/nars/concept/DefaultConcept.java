package nars.concept;

import javolution.util.function.Equality;
import nars.*;
import nars.bag.Bag;
import nars.budget.Budget;
import nars.budget.Item;
import nars.link.*;
import nars.nal.nal5.Equivalence;
import nars.nal.nal5.Implication;
import nars.nal.nal7.TemporalRules;
import nars.premise.PremiseSelector;
import nars.process.NAL;
import nars.process.TaskProcess;
import nars.task.Sentence;
import nars.task.Task;
import nars.task.TaskSeed;
import nars.term.Compound;
import nars.term.Term;
import nars.term.Variable;

import java.util.*;

import static nars.budget.BudgetFunctions.divide;
import static nars.nal.nal1.LocalRules.trySolution;


public class DefaultConcept extends Item<Term> implements Concept {

    State state;
    final long creationTime;
    long deletionTime;

    private final Term term;

    private final TaskTable questions;
    private final TaskTable quests;
    private final BeliefTable beliefs;
    private final BeliefTable goals;

    private final Bag<Sentence, TaskLink> taskLinks;
    private final Bag<TermLinkKey, TermLink> termLinks;

    /**
     * Link templates of TermLink, only in concepts with CompoundTerm Templates
     * are used to improve the efficiency of TermLink building
     */
    private final TermLinkBuilder termLinkBuilder;
    transient private final TaskLinkBuilder taskLinkBuilder;

    private Map<Object, Object> meta = null;



    transient private final Memory memory;



    private boolean constant = false;

    final static public Equality<Task> questionEquivalence = new Equality<Task>() {

        @Override
        public boolean areEqual(Task a, Task b) {
            return (a.equalParents(b));
        }

        //N/A
        @Override public int compare(Task task, Task t1) {  return 0;        }
        @Override public int hashCodeOf(Task task) { return 0; }
    };
    private final PremiseSelector premiseSelector;


    /**
     * Constructor, called in Memory.getConcept only
     *
     * @param term      A term corresponding to the concept
     * @param b
     * @param taskLinks
     * @param termLinks
     * @param memory    A reference to the memory
     */
    public DefaultConcept(final Term term, final Budget b, final Bag<Sentence, TaskLink> taskLinks, final Bag<TermLinkKey, TermLink> termLinks, BeliefTable.RankBuilder rb, PremiseSelector ps, final Memory memory) {
        super(b);


        this.term = term;
        this.memory = memory;
        this.premiseSelector = ps;

        this.creationTime = memory.time();
        this.deletionTime = creationTime - 1; //set to one cycle before created meaning it was potentially reborn

        this.beliefs = new ArrayListBeliefTable(memory.param.conceptBeliefsMax.intValue(), rb.get(this, true));
        this.goals = new ArrayListBeliefTable(memory.param.conceptGoalsMax.intValue(), rb.get(this, false));

        final int maxQuestions = memory.param.conceptQuestionsMax.intValue();
        this.questions = new ArrayListTaskTable(maxQuestions);
        this.quests = new ArrayListTaskTable(maxQuestions);

        this.taskLinks = taskLinks;
        this.termLinks = termLinks;

        this.termLinkBuilder = new TermLinkBuilder(this);
        this.taskLinkBuilder = new TaskLinkBuilder(memory);

        this.state = State.New;
        memory.emit(Events.ConceptNew.class, this);
        if (memory.logic != null)
            memory.logic.CONCEPT_NEW.hit();


    }

    /**
     * Task links for indirect processing
     */
    public Bag<Sentence, TaskLink> getTaskLinks() {
        return taskLinks;
    }

    /**
     * Term links between the term and its components and compounds; beliefs
     */
    public Bag<TermLinkKey, TermLink> getTermLinks() {
        return termLinks;
    }

    /**
     * metadata table where processes can store and retrieve concept-specific data by a key. lazily allocated
     */
    public Map<Object, Object> getMeta() {
        return meta;
    }

    @Deprecated public void setMeta(Map<Object, Object> meta) {
        this.meta = meta;
    }

    /**
     * Pending Quests to be answered by new desire values
     */
    public TaskTable getQuests() {
        return quests;
    }

    /**
     * Judgments directly made about the term Use ArrayList because of access
     * and insertion in the middle
     */
    public BeliefTable getBeliefs() {
        return beliefs;
    }

    /**
     * Desire values on the term, similar to the above one
     */
    public BeliefTable getGoals() {
        return goals;
    }

    /**
     * Reference to the memory to which the Concept belongs
     */
    public Memory getMemory() {
        return memory;
    }


    /**
     * The term is the unique ID of the concept
     */
    public Term getTerm() {
        return term;
    }



    public State getState() {
        return state;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getDeletionTime() {
        return deletionTime;
    }

    /**
     * returns the same instance, used for fluency
     */
    public Concept setState(State nextState) {

        State lastState = this.state;

        if (lastState == nextState) {
            throw new RuntimeException(toInstanceString() + " already in state " + nextState);
        }

        if (nextState == State.New)
            throw new RuntimeException(toInstanceString() + " can not return to New state ");


        if (lastState == State.Deleted)
            throw new RuntimeException(toInstanceString() + " can not exit from Deleted state");


        //ok set the state ------
        switch (this.state = nextState) {


            case Forgotten:
                getMemory().emit(Events.ConceptForget.class, this);
                break;

            case Deleted:

                if (lastState == State.Active) //emit forget event if it came directly to delete
                    getMemory().emit(Events.ConceptForget.class, this);

                deletionTime = getMemory().time();
                getMemory().emit(Events.ConceptDelete.class, this);
                break;

            case Active:
                onActive();
                getMemory().emit(Events.ConceptActive.class, this);
                break;
        }

        getMemory().updateConceptState(this);

        if (getMeta() != null) {
            for (Object m : getMeta().values()) {
                if (m instanceof ConceptReaction)
                    ((ConceptReaction)m).onState(this, getState());
            }
        }

        return this;
    }

    /** updates the concept-has-questions index if the concept transitions from having no questions to having, or from having to not having */
    public void onTableUpdated(char punctuation, int originalSize) {
        if (!isActive()) return;

        switch (punctuation) {
            /*case Symbols.GOAL:
                break;*/
            case Symbols.QUEST:
            case Symbols.QUESTION:
                if (getQuestions().isEmpty() && getQuests().isEmpty()) {
                    if (originalSize > 0) //became empty
                        getMemory().updateConceptQuestions(this);
                } else {
                    if (originalSize == 0) { //became non-empty
                        getMemory().updateConceptQuestions(this);
                    }
                }
                break;
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Concept)) return false;
        return ((Concept) obj).getTerm().equals(getTerm());
    }

    @Override
    public int hashCode() {
        return getTerm().hashCode();
    }

    /* ---------- direct processing of tasks ---------- */



    /**
     * called by concept before it fires to update any pending changes
     */
    public void updateTermLinks() {


        getTermLinks().forgetNext(
                getMemory().param.termLinkForgetDurations,
                Global.TERMLINK_FORGETTING_EXTRA_DEPTH,
                getMemory());



        getTaskLinks().forgetNext(
                getMemory().param.taskLinkForgetDurations,
                Global.TASKLINK_FORGETTING_EXTRA_DEPTH,
                getMemory());


        linkTerms(null, true);

    }

    public boolean link(Task t) {
        if (linkTask(t))
            return linkTerms(t, true);  // recursively insert TermLink
        return false;
    }

    /**
     * for batch processing a collection of tasks; more efficient than processing them individually
     */
    //TODO untested
    public void link(Collection<Task> tasks) {

        if (!ensureActiveFor("link(Collection<Task>)")) return;

        final int s = tasks.size();
        if (s == 0) return;

        if (s == 1) {
            link(tasks.iterator().next());
            return;
        }


        //aggregate a merged budget, allowing a maximum of (1,1,1)
        Budget aggregateBudget = null;
        for (Task t : tasks) {
            if (linkTask(t)) {
                if (aggregateBudget == null) aggregateBudget = new Budget(t);
                else {
                    //aggregateBudget.merge(t);
                    aggregateBudget.accumulate(t);
                }
            }
        }

        //linkToTerms the aggregate budget, rather than each task's budget separately
        if (aggregateBudget != null) {
            linkTerms(aggregateBudget, true);
        }
    }




    /**
     * To accept a new judgment as belief, and check for revisions and solutions
     *
     * @param judg      The judgment to be accepted
     * @param belief The task to be processed
     * @return Whether to continue the processing of the task
     */
    public boolean processBelief(final TaskProcess nal, Task belief) {

        final Task input = belief;

        belief = getBeliefs().add(input, this, nal);

        boolean added;

        if (belief!=input) {
//            String reason = "Unbelievable or Duplicate";
//            //String reason = input.equals(belief) ? "Duplicate" : "Unbelievable";
//                // + "compared to: " + belief
//            getMemory().removed(input, reason);
            added = false;
        }
        else {
            added = (belief!=null);
        }

        if (added) {
            /*if (task.aboveThreshold())*/
                //if (nal != null) {
            if (hasQuestions()) {
                final Task b = belief;
                //TODO move this to a subclass of TaskTable which is customized for questions. then an arraylist impl of TaskTable can iterate by integer index and not this iterator/lambda
                getQuestions().forEach( t -> trySolution(b, t, nal) );
            }
            //}
        }

        return added;
    }


    /**
     * To accept a new goal, and check for revisions and realization, then
     * decide whether to actively pursue it
     *
     * @param judg    The judgment to be accepted
     * @param goal The task to be processed
     * @return Whether to continue the processing of the task
     */
    public boolean processGoal(final TaskProcess nal, Task goal) {

        final Task input = goal;

        goal = getGoals().add(input, this, nal);

        boolean added;

        if (goal!=input) {
            //String reason = "Undesirable or Duplicate";
            //String reason = input.equals(goal) ? "Duplicate" : "Undesirable";
            // + "compared to: " + belief
            //getMemory().removed(input, reason);
            added = false;
        }
        else {
            added = (goal!=null);
        }

        if (!added) return false;

        //long then = goal.getOccurrenceTime();
        int dur = nal.memory.duration();

//        //this task is not up to date (now is ahead of then) we have to project it first
//        if(TemporalRules.after(then, now, dur)) {
//
//            nal.singlePremiseTask(task.projection(nal.memory, now, then) );
//
//            return true;
//
//        }

        if (goal.summaryGreaterOrEqual(memory.param.goalThreshold)) {

            // check if the Goal is already satisfied
            Task beliefSatisfied = getBeliefs().topRanked();

            /*float AntiSatisfaction = 0.5f; //we dont know anything about that goal yet, so we pursue it to remember it because its maximally unsatisfied
            if (beliefSatisfied != null) {

                Truth projectedTruth = beliefSatisfied.projection(goal.getOccurrenceTime(), dur);
                //Sentence projectedBelief = belief.projectionSentence(goal.getOccurrenceTime(), dur);

                boolean solved = null!=trySolution(beliefSatisfied, projectedTruth, goal, nal); // check if the Goal is already satisfied (manipulate budget)
                if (solved) {
                    AntiSatisfaction = goal.getTruth().getExpDifAbs(beliefSatisfied.getTruth());
                }
            }

            float Satisfaction = 1.0f - AntiSatisfaction;
            Truth T = BasicTruth.clone(goal.getTruth());

            T.setFrequency((float) (T.getFrequency() - Satisfaction)); //decrease frequency according to satisfaction value

            if (AntiSatisfaction >= Global.SATISFACTION_TRESHOLD && goal.sentence.truth.getExpectation() > nal.memory.param.executionThreshold.get()) {
*/
                executeGoal(goal, this, nal);
            //}
        }

        return true;
    }

    protected static void executeGoal(Task goal, Concept c, NAL nal) {

        questionFromGoal(goal, nal);

        //TODO
        //InternalExperience.experienceFromTask(nal, task, false);

        nal.memory.execute(c, goal);

    }

    final static Variable how = new Variable("?how");

    public static void questionFromGoal(final Task task, final NAL nal) {
        if (Global.QUESTION_GENERATION_ON_DECISION_MAKING || Global.HOW_QUESTION_GENERATION_ON_DECISION_MAKING) {
            //ok, how can we achieve it? add a question of whether it is fullfilled

            ArrayList<Compound> qu = new ArrayList(3);

            if (Global.HOW_QUESTION_GENERATION_ON_DECISION_MAKING) {
                if (!(task.sentence.getTerm() instanceof Equivalence) && !(task.sentence.getTerm() instanceof Implication)) {

                    Implication i1 = Implication.make(how, task.sentence.getTerm(), TemporalRules.ORDER_CONCURRENT);
                    if (i1 != null)
                        qu.add(i1);

                    Implication i2 = Implication.make(how, task.sentence.getTerm(), TemporalRules.ORDER_FORWARD);
                    if (i2 != null)
                        qu.add(i2);

                }
            }

            if (Global.QUESTION_GENERATION_ON_DECISION_MAKING) {
                qu.add(task.sentence.getTerm());
            }

            if (qu.isEmpty()) return;

            TaskSeed<Compound> t = nal.newTask()
                    .question()
                    .parent(task)
                    .occurr(task.sentence.getOccurrenceTime()) //set tense of question to goal tense)
                    .budget(task.getPriority() * Global.CURIOSITY_DESIRE_PRIORITY_MUL, task.getDurability() * Global.CURIOSITY_DESIRE_DURABILITY_MUL, 1);

            for (Compound q : qu)
                nal.deriveSingle(t.term(q));
        }
    }


    /**
     * To answer a quest or q by existing beliefs
     *
     * @param q The task to be processed
     * @return the matching task if it was not a new task to be added
     */
    public Task processQuestion(final TaskProcess nal, Task q) {


        TaskTable table = q.isQuestion() ? getQuestions() : getQuests();

        if (Global.DEBUG) {
            if (q.sentence.truth != null) {
                System.err.println(q.sentence + " has non-null truth");
                System.err.println(q.getExplanation());
                throw new RuntimeException(q.sentence + " has non-null truth");
            }
        }


        if (getMemory().answer(this, q)) {

        }

        if (!isConstant()) {
            //boolean newQuestion = table.isEmpty();

            Task match = table.add(q, questionEquivalence, this);
            if (match == q) {
                final int presize = getQuestions().size() + getQuests().size();
                onTableUpdated(q.getPunctuation(), presize);
            }
            q = match; //try solution with the original question
        }

        final long now = getMemory().time();
        if (q.isQuest()) {
            trySolution(getGoals().top(q, now), q, nal);
        } else {
            trySolution(getBeliefs().top(q, now), q, nal);
        }

        return q;
    }




    /**
     * Link to a new task from all relevant concepts for continued processing in
     * the near future for unspecified time.
     * <p>
     * The only method that calls the TaskLink constructor.
     *
     * @param task The task to be linked
     */
    protected boolean linkTask(final Task task) {

        if (!task.summaryGreaterOrEqual(memory.param.taskLinkThreshold))
            return false;

        Budget taskBudget = task;
        taskLinkBuilder.setTemplate(null);
        taskLinkBuilder.setTask(task);

        activateTaskLink(taskLinkBuilder);  // tlink type: SELF

        List<TermLinkTemplate> templates = termLinkBuilder.templates();

        if (templates == null || templates.isEmpty()) {
            //distribute budget to incoming termlinks?
            return false;
        }


        //TODO parameter to use linear division, conserving total budget
        //float linkSubBudgetDivisor = (float)Math.sqrt(termLinkTemplates.size());
        final int numTemplates = templates.size();


        float linkSubBudgetDivisor = termLinkBuilder.getNonTransforms();
        //float linkSubBudgetDivisor = (float) Math.sqrt(numTemplates);


        final Budget subBudget = divide(taskBudget, linkSubBudgetDivisor);

        taskLinkBuilder.setBudget(subBudget);

        for (int i = 0; i < numTemplates; i++) {
            TermLinkTemplate termLink = templates.get(i);

            //if (!(task.isStructural() && (termLink.getType() == TermLink.TRANSFORM))) { // avoid circular transform

            Term componentTerm = termLink.getTarget();
            if (componentTerm.equals(getTerm())) // avoid circular transform
                continue;

            Concept componentConcept = getMemory().conceptualize(componentTerm, subBudget);

            if (componentConcept != null) {

                termLink.setTargetInstance(componentConcept.getTerm());

                taskLinkBuilder.setTemplate(termLink);

                /** activate the task tlink */
                componentConcept.activateTaskLink(taskLinkBuilder);
            } else {
                //taskBudgetBalance += subBudget.getPriority();
            }

        }

        return true;
    }


    /* ---------- insert Links for indirect processing ---------- */

    /**
     * Insert a TaskLink into the TaskLink bag
     * <p>
     * called only from Memory.continuedProcess
     *
     * @param taskLink The termLink to be inserted
     * @return the tasklink which was selected or updated
     */
    public TaskLink activateTaskLink(final TaskLinkBuilder taskLink) {

        TaskLink t = getTaskLinks().update(taskLink);
        return t;
    }

    @Override
    public Term name() {
        return getTerm();
    }


    /**
     * Recursively build TermLinks between a compound and its components
     * <p>
     * called only from Memory.continuedProcess
     *
     * @param taskBudget   The BudgetValue of the task
     * @param updateTLinks true: causes update of actual termlink bag, false: just queues the activation for future application.  should be true if this concept calls it for itself, not for another concept
     * @return whether any activity happened as a result of this invocation
     */
    public boolean linkTerms(final Budget taskBudget, boolean updateTLinks) {

        final float subPriority;
        int recipients = termLinkBuilder.getNonTransforms();
        if (recipients == 0) {
            //termBudgetBalance += subBudget;
            //subBudget = 0;
            //return false;
        }

        float dur = 0, qua = 0;
        if (taskBudget != null) {
            //TODO make this parameterizable

            //float linkSubBudgetDivisor = (float)Math.sqrt(recipients);

            //half of each subBudget is spent on this concept and the other concept's termlink
            //subBudget = taskBudget.getPriority() * (1f / (2 * recipients));

            //subPriority = taskBudget.getPriority() / (float) Math.sqrt(recipients);
            subPriority = taskBudget.getPriority() / recipients;
            dur = taskBudget.getDurability();
            qua = taskBudget.getQuality();
        } else {
            subPriority = 0;
        }


        boolean activity = false;

        if (recipients > 0) {
            final List<TermLinkTemplate> templates = termLinkBuilder.templates();

            int numTemplates = templates.size();
            for (int i = 0; i < numTemplates; i++) {

                final TermLinkTemplate template = templates.get(i);

                //only apply this loop to non-transform termlink templates
                if (template.type != TermLink.TRANSFORM) {
                    if (template.link(this, subPriority, dur, qua, updateTLinks))
                        activity = true;
                }

            }

        }

        //TODO merge with above loop, or avoid altogether under certain conditions

        List<TermLinkTemplate> tl = getTermLinkTemplates();
        if (tl != null && updateTLinks) {
            int n = tl.size();
            for (int i = 0; i < n; i++) {
                TermLinkTemplate t = tl.get(i);
                if (t.summaryGreaterOrEqual(memory.param.termLinkThreshold)) {
                    if (linkTerm(t, updateTLinks))
                        activity = true;

                }
            }
        }


        return activity;
    }

    boolean linkTerm(TermLinkTemplate template, boolean updateTLinks) {
        return template.link(this, 0,0, 0, updateTLinks);
        //return linkTerm(template, 0, 0, 0, updateTLinks);
    }

    @Override
    public TermLinkBuilder getTermLinkBuilder() {
        return termLinkBuilder;
    }

    /**
     * Insert a new or activate an existing TermLink in the TermLink bag
     * via a caching TermLinkSelector which has been configured for the
     * target Concept and the current budget
     * <p>
     * called from buildTermLinks only
     * <p>
     * If the tlink already exists, the budgets will be merged
     *
     * @param termLink The termLink to be inserted
     * @return the termlink which was selected or updated
     */
    public TermLink activateTermLink(final TermLinkBuilder termLink) {

        return getTermLinks().update(termLink);

    }



    /**
     * Return a string representation of the concept, called in ConceptBag only
     *
     * @return The concept name, with taskBudget in the full version
     */
    @Override
    public String toString() {  // called from concept bag
        //return (super.toStringBrief() + " " + key);
        //return super.toStringExternal();
        return getTerm().toString();
    }

    /**
     * called from {@link NARRun}
     */
    @Override
    public String toStringLong() {
        String res =
                toStringWithBudget() + " " + getTerm().name()
                        + toStringIfNotNull(getTermLinks().size(), "termLinks")
                        + toStringIfNotNull(getTaskLinks().size(), "taskLinks")
                        + toStringIfNotNull(getBeliefs().size(), "beliefs")
                        + toStringIfNotNull(getGoals().size(), "goals")
                        + toStringIfNotNull(getQuestions().size(), "questions")
                        + toStringIfNotNull(getQuests().size(), "quests");

        //+ toStringIfNotNull(null, "questions");
        /*for (Task t : questions) {
            res += t.toString();
        }*/
        // TODO other details?
        return res;
    }

    private String toStringIfNotNull(final Object item, final String title) {
        if (item == null) {
            return "";
        }

        final String itemString = item.toString();

        return new StringBuilder(2 + title.length() + itemString.length() + 1).
                append(' ').append(title).append(':').append(itemString).toString();
    }

//    /**
//     * Recalculate the quality of the concept [to be refined to show
//     * extension/intension balance]
//     *
//     * @return The quality value
//     */
//    public float getAggregateQuality() {
//        float linkPriority = termLinks.getPriorityMean();
//        float termComplexityFactor = 1.0f / term.getComplexity();
//        float result = or(linkPriority, termComplexityFactor);
//        if (result < 0) {
//            throw new RuntimeException("Concept.getQuality < 0:  result=" + result + ", linkPriority=" + linkPriority + " ,termComplexityFactor=" + termComplexityFactor + ", termLinks.size=" + termLinks.size());
//        }
//        return result;
//    }


    @Override public void delete() {

        if (getMemory().inCycle())
            throw new RuntimeException("concept " + this + " attempt to delete() during an active cycle; must be done between cycles");

        if (isDeleted()) return;

        //called first to allow listeners to have a final attempt to interact with this concept before it dies
        setState(State.Deleted);

        {
            //completely remove from active bags and concept indexes

            memory.concepts.remove(getTerm());
            Concept removed = memory.getControl().remove(this);
            if (removed!=null && removed != this) {
                throw new RuntimeException("Duplicate instances of Concepts with same term: " + this + " (deleting) " + removed);
            }
        }

        zero();

        super.delete();

        //dont delete the tasks themselves because they may be referenced from othe concepts.
        beliefs.clear();
        goals.clear();
        questions.clear();
        quests.clear();


        if (getMeta() != null) {
            getMeta().clear();
            setMeta(null);
        }

        getTermLinks().delete();
        getTaskLinks().delete();

        if (termLinkBuilder != null)
            termLinkBuilder.delete();
    }
//
//
//    /**
//     * Collect direct isBelief, questions, and goals for display
//     *
//     * @return String representation of direct content
//     */
//    public String displayContent() {
//        final StringBuilder buffer = new StringBuilder(18);
//        buffer.append("\n  Beliefs:\n");
//        if (!beliefsEternal.isEmpty()) {
//            for (Sentence s : beliefsEternal) {
//                buffer.append(s).append('\n');
//            }
//        }
//        if (!beliefsTemporal.isEmpty()) {
//            for (Sentence s : beliefsTemporal) {
//                buffer.append(s).append('\n');
//            }
//        }
//        if (!questions.isEmpty()) {
//            buffer.append("\n  Question:\n");
//            for (Task t : questions) {
//                buffer.append(t).append('\n');
//            }
//        }
//        return buffer.toString();
//    }


    public List<TermLinkTemplate> getTermLinkTemplates() {
        return termLinkBuilder.templates();
    }


    /**
     * Pending Question directly asked about the term
     *
     * Note: since this is iterated frequently, an array should be used. To
     * avoid iterator allocation, use .get(n) in a for-loop
     */
    /**
     * Return the questions, called in ComposionalRules in
     * dedConjunctionByQuestion only
     */
    public TaskTable getQuestions() {
        return questions;
    }



    @Override
    public boolean isConstant() {
        return constant;
    }

    @Override
    public boolean setConstant(boolean b) {
        this.constant = b;
        return constant;
    }

    //
//    public Collection<Sentence> getSentences(char punc) {
//        switch(punc) {
//            case Symbols.JUDGMENT: return beliefs;
//            case Symbols.GOAL: return goals;
//            case Symbols.QUESTION: return Task.getSentences(questions);
//            case Symbols.QUEST: return Task.getSentences(quests);
//        }
//        throw new RuntimeException("Invalid punctuation: " + punc);
//    }
//    public CharSequence getBeliefsSummary() {
//        if (beliefs.isEmpty())
//            return "0 beliefs";
//        StringBuilder sb = new StringBuilder();
//        for (Sentence s : beliefs)
//            sb.append(s.toString()).append('\n');
//        return sb;
//    }
//    public CharSequence getGoalSummary() {
//        if (goals.isEmpty())
//            return "0 goals";
//        StringBuilder sb = new StringBuilder();
//        for (Sentence s : goals)
//            sb.append(s.toString()).append('\n');
//        return sb;
//    }


    /**
     * called when concept is activated; empty and subclassable
     */
    protected void onActive() {

    }

    @Override
    public TermLink nextTermLink(TaskLink taskLink) {
        return premiseSelector.nextTermLink(this, taskLink);
    }

}
