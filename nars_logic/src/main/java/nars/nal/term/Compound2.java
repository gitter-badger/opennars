package nars.nal.term;

import nars.util.data.Hash2;
import nars.util.data.Util;

import java.util.Arrays;

/** an optimized compound implementation for use when only 1 subterm */
abstract public class Compound2<A extends Term,B extends Term> extends Compound  {

    protected Compound2(A a, B b) {
        super(a, b);
    }

    public A a() {
        return (A)term[0];
    }
    public B b() {
        return (B)term[1];
    }


    @Override final public int length() {
        return 2;
    }

    @Override
    public void invalidate() {
        if (hasVar()) {
            this.id = null; //invalidate name so it will be (re-)created lazily

            for (final Term t : term) {
                if (t instanceof Compound)
                    ((Compound) t).invalidate();
            }

        }
        else {
            setNormalized();
        }
    }

    @Override
    public boolean equals(final Object that) {
        if (this == that) return true;
        if (that == null) return false;

        if (getClass() != that.getClass()) return false;

        Compound2 c = (Compound2) that;
        return equalID(c);
    }

    public boolean equalsByContent(final Object that) {


        Compound2 c = (Compound2) that;

            //compare components without generating name

            //if (operator()!=c.operator()) return false;

            if (getTemporalOrder() != c.getTemporalOrder()) return false;
            if (getComplexity() != c.getComplexity()) return false;

            if (a().equals(c.a()) && b().equals(c.b())) {
                share(c);
                return true;
            }
        return false;

    }


//    @Override
//    public int hashCode2() {
//        //return hashCode();
//        return Util.hash(operator().ordinal(), getTemporalOrder(), b(), a() );
//    }

    /** compares only the contents of the subterms; assume that the other term is of the same operator type */
    @Override
    public int compareSubterms(final Compound otherCompoundOfEqualType) {
        //this is what we want to avoid - generating string names
        //override in subclasses where a different non-string comparison can be made


        final Compound2 other = ((Compound2) otherCompoundOfEqualType);

        int ca = a().compareTo(other.a());
        if (ca != 0) return ca;

        int cb = b().compareTo(other.b());

//        if (Global.DEBUG) {
//            if ((cb == 0) && (!otherCompoundOfEqualType.equals(this))) {
//                throw new RuntimeException("inconsistent compareTo: " + ca + ", " + cb + " =?" + otherCompoundOfEqualType.equals(this));
//            }
//        }
        return cb;
    }



}
