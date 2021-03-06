package nars.nal;

import nars.Events;
import nars.NAR;
import nars.util.event.EventEmitter;
import nars.util.event.Reaction;


public abstract class AbstractController implements Reaction<Class> {

    public final NAR nar;
    /** how many cycles to wait before action, then wait again.. */
    private int period;
    private EventEmitter.Registrations reg;

    /** how many cycles to wait before action, then wait again.. */
    public AbstractController(NAR n, int period) {
        this.nar = n;
        this.period = period;
        start();
    }
    /** read sensor values as input */
    /** adjust parameter values */

    public void start() {
        this.reg = nar.on(this, Events.CycleEnd.class);
    }

    /** read sensor values as input */
    public abstract void getSensors();

    /** adjust parameter values */
    public abstract void setParameters();

    @Override
    public void event(final Class event, final Object... arguments) {
        //TODO use relative time (not modulo) for non-sequence time modes
        long cycle = nar.time();
        getSensors();
        if (cycle % period == (period - 1)) {
            setParameters();
        }
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public void stop() {
        if (reg!=null) {
            reg.off();
            reg = null;
        }
    }
}
