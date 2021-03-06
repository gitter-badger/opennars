package nars.cycle;

import nars.Global;
import nars.Memory;
import nars.bag.Bag;
import nars.concept.Concept;
import nars.io.Perception;
import nars.link.TaskLink;
import nars.process.ConceptProcess;
import nars.process.TaskProcess;
import nars.task.Sentence;
import nars.task.Task;
import nars.task.TaskAccumulator;
import nars.term.Compound;
import nars.term.Term;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The original deterministic memory cycle implementation that is currently used as a standard
 * for development and testing.
 */
public class DefaultCycle extends SequentialCycle {



    /** How many concepts to fire each cycle; measures degree of parallelism in each cycle */
    public final AtomicInteger conceptsFiredPerCycle;

    /** max # of inputs to perceive per cycle; -1 means unlimited (attempts to drains input to empty each cycle) */
    public final AtomicInteger inputsMaxPerCycle;

    /** max # of novel tasks to process per cycle; -1 means unlimited (attempts to drains input to empty each cycle) */
    public final AtomicInteger novelMaxPerCycle;



    /**
     * New tasks with novel composed terms, for delayed and selective processing
     */
    public final Bag<Sentence<Compound>, Task<Compound>> novelTasks;

    int numNovelTasksPerCycle = 1;

    /* ---------- Short-term workspace for a single cycle ------- */
    /**
     * List of new tasks accumulated in one cycle, to be processed in the next
     * cycle
     */
    protected final TaskAccumulator newTasks;
    protected Set<Task> newTasksTemp = Global.newHashSet(8);
    protected boolean executingNewTasks = false;



    public DefaultCycle(TaskAccumulator newTasks, Bag<Term, Concept> concepts, Bag<Sentence<Compound>, Task<Compound>> novelTasks, AtomicInteger inputsMaxPerCycle, AtomicInteger novelMaxPerCycle, AtomicInteger conceptsFiredPerCycle) {
        super(concepts);

        this.newTasks = newTasks;
        this.conceptsFiredPerCycle = conceptsFiredPerCycle;
        this.inputsMaxPerCycle = inputsMaxPerCycle;
        this.novelMaxPerCycle = novelMaxPerCycle;
        this.novelTasks = novelTasks;
    }


    @Override
    public void delete() {
        super.delete();
        novelTasks.delete();
    }

    @Override
    public void reset(Memory m, Perception p) {
        super.reset(m, p);

        newTasksTemp.clear();
        newTasks.clear();

        novelTasks.clear();
    }

    @Override
    public boolean onTask(Task t) {
        if (executingNewTasks) {
            return newTasksTemp.add(t); //buffer it
        }
        else {
            return newTasks.add(t); //add it directly to the newtasks set
        }
    }


    /**
     * An atomic working cycle of the system:
     *  0) optionally process inputs
     *  1) optionally process new task(s)
     *  2) optionally process novel task(s)
     *  2) optionally fire a concept
     **/
    @Override
    public synchronized void cycle() {

        concepts.forgetNext(
                memory.param.conceptForgetDurations,
                Global.CONCEPT_FORGETTING_EXTRA_DEPTH,
                memory);

        //inputs
        inputNextPerception(inputsMaxPerCycle.get());


        //all new tasks
        int numNewTasks = newTasks.size();
        if (numNewTasks > 0)
            runNewTasks();


        //1 novel tasks if numNewTasks empty
        if (newTasks.isEmpty() && !novelTasks.isEmpty())  {
            int nn = novelMaxPerCycle.get();
            if (nn < 0) nn = novelTasks.size(); //all
            if (nn > 0)
                runNextNovelTasks(nn);
        }


        //1 concept if (memory.newTasks.isEmpty())*/
        int conceptsToFire = newTasks.isEmpty() ? conceptsFiredPerCycle.get() : 0;

        float tasklinkForgetDurations = memory.param.taskLinkForgetDurations.floatValue();
        float conceptForgetDurations = memory.param.conceptForgetDurations.floatValue();
        for (int i = 0; i < conceptsToFire; i++) {
            ConceptProcess f = newProcess(nextConceptToProcess(conceptForgetDurations), tasklinkForgetDurations);
            if (f != null) {
                f.run();
            }
        }

        memory.runNextTasks();

    }

    private void runNewTasks() {

        queueNewTasks();

        for (int n = newTasks.size()-1;  n >= 0; n--) {
            Task highest = newTasks.removeHighest();
            if (highest == null) break;

            run(highest);
        }

        commitNewTasks();

    }

    /**
     * Select a novel task to process.
     */
    protected void runNextNovelTasks(int count) {

        queueNewTasks();

        for (int i = 0; i < count; i++) {

            final Task task = novelTasks.pop();
            if (task!=null)
                TaskProcess.run(memory, task);
            else
                break;
        }

        commitNewTasks();
    }

    /** should be followed by a 'commitNewTasks' call after finishing */
    private void queueNewTasks() {
        executingNewTasks = true;
    }

    private void commitNewTasks() {

        executingNewTasks = false;

        //add the generated tasks back to newTasks
        int ns = newTasksTemp.size();
        if (ns > 0) {
            newTasks.addAll( newTasksTemp );
            newTasksTemp.clear();
        }
    }

    /** returns whether the task was run */
    protected boolean run(Task task) {
        final Sentence sentence = task.sentence;

        //memory.emotion.busy(task);

        if (task.isInput() || !(sentence.isJudgment())
                || (memory.concept(sentence.getTerm()) != null)
                ) {

            //it is a question/quest or a judgment for a concept which exists:
            return TaskProcess.run(memory, task)!=null;

        } else {
            //it is a judgment or goal which would create a new concept:


            final Sentence s = sentence;

            //if (s.isJudgment() || s.isGoal()) {

            final double exp = s.truth.getExpectation();
            if (exp > memory.param.conceptCreationExpectation.floatValue()) {//Global.DEFAULT_CREATION_EXPECTATION) {

                // new concept formation
                Task overflow = novelTasks.put(task);

                if (overflow != null) {
                    if (overflow == task) {
                        memory.removed(task, "Ignored");
                        return false;
                    } else {
                        memory.removed(overflow, "Displaced novel task");
                    }
                }

                memory.logic.TASK_ADD_NOVEL.hit();
                return true;

            } else {
                memory.removed(task, "Neglected");
            }
            //}
        }
        return false;
    }


    private ConceptProcess newProcess(final Concept concept, float taskLinkForgetDurations) {
        if (concept == null) return null;


        TaskLink taskLink = concept.getTaskLinks().forgetNext(taskLinkForgetDurations, memory);
        if (taskLink!=null)
            return new ConceptProcess(memory, concept, taskLink);
        else {
            return null;
        }

    }




}
      /*
    //private final Cycle loop = new Cycle();
    class Cycle {
        public final AtomicInteger threads = new AtomicInteger();
        private final int numThreads;

        public Cycle() {
            this(Parameters.THREADS);
        }

        public Cycle(int threads) {
            this.numThreads = threads;
            this.threads.set(threads);

        }

        int t(int threads) {
            if (threads == 1) return 1;
            else {
                return threads;
            }
        }




        public int newTasksPriority() {
            return memory.newTasks.size();
        }

        public int novelTasksPriority() {
            if (memory.getNewTasks().isEmpty()) {
                return t(numThreads);
            } else {
                return 0;
            }
        }

        public int conceptsPriority() {
            if (memory.getNewTasks().isEmpty()) {
                return memory.param.conceptsFiredPerCycle.get();
            } else {
                return 0;
            }
        }


    }

    */
