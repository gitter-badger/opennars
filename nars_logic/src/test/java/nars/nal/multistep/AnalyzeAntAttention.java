/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package nars.nal.multistep;

import nars.NAR;
import nars.nar.Neuromorphic;

import java.io.File;

/**
 *
 * @author me
 */
public class AnalyzeAntAttention {
    
    public static void main(String[] args) throws Exception {
        
        boolean showOutput = true;

        NAR nar = new NAR(new Neuromorphic(1));

        nar.input(new File("nal/test/nal7.15.nal"));
            
                
//        new NALTestSome("nal/test/nal7.15.nal", showOutput) {
//        //new NALTestSome("nal/test/nal1.multistep.nal", showOutput) {
//        //new NALTestSome("nal/test/nars_multistep_1.nal", showOutput) {
//
//            @Override
//            public NAR newNAR() {
//                
//                return nar;
//            }
//          
//            
//        }.test();
                
        
//        //nar.addInput(new TextInput(new File("nal/Examples/Example-MultiStep-edited.txt")));
//        //nar.addInput(new TextInput(new File("nal/Examples/Example-NAL1-edited.txt")));
//        nar.addInput(new TextInput(new File("nal/test/nal1.multistep.nal")));
//        nar.finish(10);
        
        
    }    
    
}
