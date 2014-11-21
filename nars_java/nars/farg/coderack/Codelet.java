/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */

package nars.farg.coderack;

import nars.core.Memory;
import nars.core.control.NAL;
import nars.entity.BudgetValue;
import nars.entity.Item;
import nars.language.Term;

/**
 *
 * @author patrick.hammer
 */
public class Codelet extends Item<Term> {
    //Codelet is a small amount of code that has a chance to be run.
    //since I believe NAL makes this idea obsolete to a large extent,
    //here we concentrate on parts which NAL can't make obsolete
    //like codelets which break up structures,
    //and judge the relevance of concepts
    //according to success related to goals and questions
    
    //things where i dont see much sense in NARS context:
    //Answer (question answering instead)
    //Rule (NAL inference instead)
    //Correspondence
    
    //break may make sense but this we have to evaluate..
    
    Object args;
    public int timestamp;
    public Object bin=null;
    Term t;
    Memory mem;
    public static int codeletid=0;
    
    public Codelet(BudgetValue budget, Memory mem, Object args) {
        super(budget);
        this.args=args;
        this.mem=mem;
        t=new Term("Codelet"+String.valueOf(codeletid));
        codeletid++;
    }
    
    
    public void run() {}

    @Override
    public Term name() {
        return t; //To change body of generated methods, choose Tools | Templates.
    }
}