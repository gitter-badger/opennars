package nars.util.index;

import nars.NAR;
import nars.concept.Concept;
import nars.term.Term;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** uses a predefined set of terms that will be mapped */
abstract public class ConceptSet<T extends Term> extends MutableConceptMap<T> implements Iterable<T> {

    public final Map<T,Concept> values = new LinkedHashMap();


    public ConceptSet(NAR nar) {
        super(nar);
    }

    @Override
    public Iterator<T> iterator() {
        return values.keySet().iterator();
    }

    public boolean include(Concept c) {
        Concept removed = values.put((T) c.getTerm(), c);
        return removed!=c; //different instance
    }
    public boolean exclude(Concept c) {
        return values.remove(c.getTerm())!=null;
    }
    public boolean exclude(Term t) {
        return values.remove(t)!=null;
    }


    public boolean contains(final T t) {
        if (!values.containsKey(t)) {
            return super.contains(t);
        }
        return true;
    }


    /** set a term to be present always in this map, even if the conept disappears */
    public void include(T a) {
        super.include(a);
        values.put(a, null);
    }

    /** remove an inclusion, and/or add an exclusion */
    //TODO public void exclude(Term a) { }

    public int size() {
        return values.size();
    }


}
