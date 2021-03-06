package nars.meter;

import nars.Global;
import nars.Memory;
import nars.nal.nal1.Inheritance;
import nars.nal.nal3.SetInt;
import nars.nal.nal4.Product;
import nars.nal.nal8.Operation;
import nars.op.mental.InternalExperience;
import nars.op.mental.consider;
import nars.op.mental.remind;
import nars.process.ConceptProcess;
import nars.process.NAL;
import nars.process.TaskProcess;
import nars.task.Task;
import nars.term.Atom;
import nars.term.Compound;
import nars.util.meter.event.DoubleMeter;
import org.apache.commons.math3.stat.descriptive.moment.Mean;

import java.io.Serializable;

/**
 * emotional value; self-felt internal mental states; variables used to record emotional values
 */
public class EmotionMeter implements Serializable {

    public static final Compound BUSYness = SetInt.make(Atom.the("busy"));
    private final Memory memory;

    /**
     * average happiness accumulated in this cycle
     */
    private Mean happy = new Mean();

    /**
     * average priority
     */
    private float busy;

    public float lasthappy = -1;
    public float lastbusy = -1;


    public final DoubleMeter happyMeter = new DoubleMeter("happy");
    public final DoubleMeter busyMeter = new DoubleMeter("busy");

    public static final Atom satisfied = Atom.the("satisfied");
    final static Compound satisfiedSetInt = SetInt.make(satisfied);

    public EmotionMeter(Memory memory) {
        this.memory = memory;
    }

    public float happy() {
        if (happy.getN()==0)
            return 0f;

        return (float)happy.getResult();
    }

    public float busy() {
        return busy;
    }


    public void happy(final float solution, final Task task, final NAL nal) {
        this.happy.increment( task.getQuality() * solution );
    }

    protected void commitHappy() {

        final float currentHappy = happy();

        if (lasthappy != -1) {
            float frequency = changeSignificance(lasthappy, currentHappy, Global.HAPPY_EVENT_CHANGE_THRESHOLD);
//            if (happy > Global.HAPPY_EVENT_HIGHER_THRESHOLD && lasthappy <= Global.HAPPY_EVENT_HIGHER_THRESHOLD) {
//                frequency = 1.0f;
//            }
//            if (happy < Global.HAPPY_EVENT_LOWER_THRESHOLD && lasthappy >= Global.HAPPY_EVENT_LOWER_THRESHOLD) {
//                frequency = 0.0f;
//            }

            if ((frequency != -1) && (memory.nal(7))) { //ok lets add an event now

                Inheritance inh = Inheritance.make(memory.self(), satisfiedSetInt);

                memory.add(
                        memory.newTask(inh).judgment()
                                .truth(frequency, Global.DEFAULT_JUDGMENT_CONFIDENCE)
                                .occurrNow()
                                .budget(Global.DEFAULT_JUDGMENT_PRIORITY, Global.DEFAULT_JUDGMENT_DURABILITY)
                                .reason("Happy Metabelief")
                                .get()
                );

                if (Global.REFLECT_META_HAPPY_GOAL) { //remind on the goal whenever happyness changes, should suffice for now

                    //TODO convert to fluent format

                    memory.add(
                            memory.newTask(inh).goal()
                                    .truth(frequency, Global.DEFAULT_GOAL_CONFIDENCE)
                                    .occurrNow()
                                    .budget(Global.DEFAULT_GOAL_PRIORITY, Global.DEFAULT_GOAL_DURABILITY)
                                    .reason("Happy Metagoal")
                                    .get()
                    );

                    //this is a good candidate for innate belief for consider and remind:

                    if (InternalExperience.enabled && Global.CONSIDER_REMIND) {
                        Operation op_consider = Operation.make(consider.consider, Product.only(inh));
                        Operation op_remind = Operation.make(remind.remind, Product.only(inh));

                        //order important because usually reminding something
                        //means it has good chance to be considered after
                        for (Operation o : new Operation[]{op_remind, op_consider}) {

                            memory.add(
                                    memory.newTask(o).judgment()
                                            .occurrNow()
                                            .truth(1.0f, Global.DEFAULT_JUDGMENT_CONFIDENCE)
                                            .budget(Global.DEFAULT_JUDGMENT_PRIORITY * InternalExperience.INTERNAL_EXPERIENCE_PRIORITY_MUL,
                                                    Global.DEFAULT_JUDGMENT_DURABILITY * InternalExperience.INTERNAL_EXPERIENCE_DURABILITY_MUL)
                                            .reason("Happy Remind/Consider")
                                            .get()
                            );
                        }
                    }
                }
            }
        }

        happyMeter.set(currentHappy);

        this.happy.clear();
    }

    /** @return -1 if no significant change, 0 if decreased, 1 if increased */
    private float changeSignificance(float prev, float current, float proportionChangeThreshold) {
        float range = Math.max(prev, current);
        if (range == 0) return -1;
        if (prev - current > range * proportionChangeThreshold)
            return -1;
        else if (current - prev > range * proportionChangeThreshold)
            return 1;

        return -1;
    }

    public void busy(ConceptProcess nal) {
        busy(nal.getTask(), nal);
    }

    public void busy(TaskProcess nal) {
        busy(nal.getTask(), nal);
    }

    protected void busy(Task cause, NAL nal) {

        this.busy += cause.getPriority();
    }


    protected void commitBusy() {

        if (lastbusy != -1) {
            //float frequency = -1;
            float frequency = changeSignificance(lastbusy, busy, Global.BUSY_EVENT_CHANGE_THRESHOLD);
            //            if (busy > Global.BUSY_EVENT_HIGHER_THRESHOLD && lastbusy <= Global.BUSY_EVENT_HIGHER_THRESHOLD) {
//                frequency = 1.0f;
//            }
//            if (busy < Global.BUSY_EVENT_LOWER_THRESHOLD && lastbusy >= Global.BUSY_EVENT_LOWER_THRESHOLD) {
//                frequency = 0.0f;
//            }


            if ((frequency != -1) && (memory.nal(7))) { //ok lets add an event now
                final Inheritance busyTerm = Inheritance.make(memory.self(), BUSYness);

                memory.add(
                        memory.newTask(busyTerm).judgment()
                                .truth(frequency, Global.DEFAULT_JUDGMENT_CONFIDENCE)
                                .occurrNow()
                                .budget(Global.DEFAULT_JUDGMENT_PRIORITY, Global.DEFAULT_JUDGMENT_DURABILITY)
                                .reason("Busy")
                                .get()
                );
            }
        }

        busyMeter.set(lastbusy = this.busy);

        this.busy = 0;


    }

    public void commit() {
        commitHappy();

        commitBusy();
    }

    public void clear() {
        happy.clear();
        busy = 0;
    }
}
