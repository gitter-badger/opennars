package nars.gui.output;

import automenta.vivisect.face.HumanoidFacePanel;
import nars.NAR;
import nars.Global;



public class NARFacePanel extends HumanoidFacePanel  {
    private final NAR nar;

    public NARFacePanel(NAR n) {
        super();
        this.nar = n;
    }
    
    @Override
    public void visibility(boolean appearedOrDisappeared) {
        super.visibility(appearedOrDisappeared);
        
//        if (showing) {
//            nar.addOutput(this);
//        }
//        else {
//            nar.removeOutput(this);
//        }
    }
    

    float smoothHappy = 0;

    @Override
    public void update(double t) {
        float currentHappy = nar.memory.emotion.happy();
        if (currentHappy > smoothHappy)
            smoothHappy = currentHappy;
        else
            smoothHappy*= 0.5;


        happy = smoothHappy > 0.5;
        unhappy = smoothHappy < 0.1;


        float conceptPriority; //((Number)nar.memory.logic.get("concept.priority.mean")).floatValue();
        float taskNewPriority = 0.5f; //((Number)nar.memory.logic.get("task.new.priority.mean")).floatValue();        

        float busy = nar.memory.emotion.busy();
        //max out at 0.5
        conceptPriority = Math.min(1f, busy); //Math.min(conceptPriority, 0.4f);
        //if (nar.memory.getConcepts().isEmpty())
            //conceptPriority = 0; //if no concepts, start at zero, regardless of what mean might be valued
        
        face.setPupil(12f * (conceptPriority+0.35f)+2f,
                conceptPriority*0.45f,0,0,0.9f); //pupils glow a little red for priority of new tasks
        
        face.setEyeball(8f * (conceptPriority + 0.35f)+12f,1f,1f,1f,0.85f);

        nodFreq = (float)Math.log(1+ nodFreq);
        nod = busy > 0.1f;
        shake = busy > 0.7f;

        super.update(t);        
    }

//    @Override
//    public void output(Class channel, Object signal) {
//        talk=1;
//    }
    
}
