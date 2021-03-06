package objenome.op.cas;

import objenome.op.cas.util.ArrayLists;

import java.util.ArrayList;
import java.util.HashMap;

public class Product extends Operation {
    
    private ArrayList<Expr> exprs;
    
    private <E extends Expr> Product(ArrayList<E> exprs) {
        this.exprs = ArrayLists.castAll(exprs, Expr.class);
    }
    
    public static Expr make(ArrayList<? extends Expr> exprs) {
        return make(exprs, true);
    }
    
    public static Expr make(ArrayList<? extends Expr> exprs, boolean simplify) {
        Product product = new Product(exprs);
        return simplify ? product.simplify() : product;
    }
    
    public static Expr makeDefined(ArrayList<? extends Expr> exprs) {
        return make(exprs);
    }
    
    public static Expr make(Expr expr1, Expr expr2) {
        return make(expr1, expr2, true);
    }
    
    public static Expr make(Expr expr1, Expr expr2, boolean simplify) {
        ArrayList<Expr> tmp1 = new ArrayList<Expr>(2);
        tmp1.add(expr1);
        tmp1.add(expr2);
        return make(tmp1, simplify);
    }
    
    public static Expr negative(Expr expr) {
        ArrayList<Expr> tmp1 = new ArrayList<Expr>(2);
        tmp1.add(Num.make(-1));
        tmp1.add(expr);
        return make(tmp1);
    }
    
    public Expr deriv(Var respected) {
        // if (debug) System.err.println("derivative of " + dump());
        if (exprs.isEmpty()) return Num.make();
        if (exprs.size() == 1) return exprs.get(0).deriv(respected);
        if (exprs.size() == 2) {
            // if (debug) System.err.println(dump() + " => " + Sum.make(Product.make(exprs.get(0), exprs.get(1).deriv(dy, dx)), Product.make(exprs.get(1), exprs.get(0).deriv(dy, dx))));
            return Sum.make(Product.make(exprs.get(0), exprs.get(1).deriv(respected)), Product.make(exprs.get(0).deriv(respected), exprs.get(1)));
        }
        // if (debug) System.err.println(ArrayLists.dumpAll(exprs));
        return Product.make(new Product(new ArrayList<Expr>(exprs.subList(0, exprs.size() - 1))), exprs.get(exprs.size() - 1), false).deriv(respected);
    }
    
    public ArrayList<Expr> getExprs() {
        return (ArrayList<Expr>) exprs.clone();
    }

    public Expr simplify() {
        Product other = this;
        simplify:
        while (true) {
            Expr conditioned = other.conditioned();
            if (conditioned != null) return conditioned;

            ArrayList<Expr> bottoms = new ArrayList<Expr>();
            for (int i = 0; i < other.exprs.size(); i++) {
                Expr expr = other.exprs.get(i);
                if (expr instanceof Division) {
                    bottoms.add(((Operation) expr).getExpr(1));
                    other.exprs.set(i, ((Operation) expr).getExpr(0));
                }
            }
            if (!bottoms.isEmpty()) return Division.make(Product.make(other.exprs), Product.make(bottoms));


            ArrayList<Double> numbers = new ArrayList<Double>();
            ArrayList<Expr> constants = new ArrayList<Expr>();
            for (int i = 0; i < other.exprs.size(); i++) {
                Expr expr = other.exprs.get(i);
                // if (debug) System.err.println("simplify: on expr: " + expr);
                if (/*interpolate &&*/ expr instanceof Product) {
                    other.exprs.remove(i);
                    other.exprs.addAll(i, ((Product) expr).getExprs());
                    i--;
                } else if (expr instanceof Num) {
                    if (((Num) expr).val() == 0) {
                        other.exprs.clear();
                        return Num.make();
                    }

                    other.exprs.remove(i);
                    i--;

                    numbers.add(((Num) expr).val());
                } else if (expr.isConstant()) {
                    constants.add(other.exprs.remove(i));
                    i--;
                }
            }

            other.exprs.addAll(0, constants);

            for (int i = 0; i < numbers.size(); i++) {
                for (int j = i + 1; j < numbers.size(); j++) {
                    if (numbers.get(i) * numbers.get(j) / numbers.get(i) / numbers.get(j) == 1 && numbers.get(j) * numbers.get(i) / numbers.get(j) / numbers.get(i) == 1) {
                        numbers.set(i, numbers.get(i) * numbers.remove(j));
                        j = numbers.size();
                        i = -1;
                    }
                }
            }

            while (numbers.remove(1d)) {
            }
            if (numbers.isEmpty()) numbers.add(1d);

            if (other.exprs.isEmpty() && numbers.size() == 1) return Num.make(numbers.get(0));

            if (!(numbers.size() == 1 && numbers.get(0) == 1)) {
                ArrayList<Expr> tmp = new ArrayList<Expr>();
                for (Double number : numbers) {
                    tmp.add(Num.make(number));
                }
                other.exprs.addAll(0, tmp);
            }

            if (other.exprs.size() == 1) return other.exprs.get(0);

            // if (debug) System.err.println("Product simplify: " + dump());
            for (int i = 0; i < other.exprs.size(); i++) {
                Expr expr = other.exprs.get(i);
                for (int j = 0; j < other.exprs.size(); j++) {
                    if (i != j) {
                        Expr expr2 = other.exprs.get(j);
                        if (expr2 instanceof Division) {
                            // if (debug) System.err.println("Product simplify: expr2: " + expr2);
                            ArrayList<Expr> divExprs = ((Division) expr2).getExprs();
                            // if (debug) System.err.println("Product simplify: dividing (" + expr + ")/(" + divExprs.get(1) + ")");
                            Expr divided = Division.make(expr, divExprs.get(1));
                            // if (debug) System.err.println("Product simplify: divided: " + divided);
                            if (!(divided instanceof Division)) {
                                other.exprs.set(i, divided);
                                other.exprs.set(j, divExprs.get(0));
                                if (other.debug) System.err.println("Product.simplify: divided to: " + other.dump());
                                continue simplify;
                            }
                            if (!((Operation) divided).getExprs().get(0).equals(expr)) {
                                other.exprs.remove(i);
                                other.exprs.set(other.exprs.indexOf(expr2), Division.make(Product.make(expr, divExprs.get(0)), divExprs.get(1)));
                                if (other.debug) System.err.println("Product.simplify: divided to: " + other.dump());
                                continue simplify;
                            }
                        }
                        if (expr.equalsExpr(expr2)) {
                            other.exprs.set(j, Exponent.make(expr, Num.make(2)));
                            other.exprs.remove(i);
                            continue simplify;
                        }
                        if (expr instanceof Exponent && expr2.equalsExpr(((Operation) expr).getExpr(0))) {
                            other.exprs.set(j, Exponent.make(expr2, Sum.make(((Operation) expr).getExpr(1), Num.make(1))));
                            other.exprs.remove(i);
                            continue simplify;
                        }
                        if (expr instanceof Exponent && expr2 instanceof Exponent && ((Operation) expr).getExpr(0).equalsExpr(((Operation) expr2).getExpr(0))) {
                            other.exprs.set(j, Exponent.make(((Operation) expr).getExpr(0), Sum.make(((Operation) expr).getExpr(1), ((Operation) expr2).getExpr(1))));
                            other.exprs.remove(i);
                            continue simplify;
                        }
                        if (!(expr instanceof Operation) && expr2 instanceof Sum) {
                            ArrayList<Expr> products = new ArrayList<Expr>();
                            for (Expr addend : ((Operation) expr2).getExprs()) {
                                products.add(Product.make(expr, addend));
                            }
                            other.exprs.set(j, Sum.make(products));
                            other.exprs.remove(i);
                            continue simplify;
                        }
                    }
                }
            }

            return other;
        }
    }
    
    public String pretty() {
        if (exprs.size() == 1) return exprs.get(0).pretty();
        
        String string = "";
        Integer classOrder = this.classOrder();
        boolean lastMinus;
        boolean nowMinus = false;
        Expr lastExpr = null;
        boolean lastParens;
        boolean parens = false;
        
        for (int i = 0; i < exprs.size(); i++) {
            Expr expr = exprs.get(i);
            
            lastMinus = nowMinus;
            nowMinus = false;
            
            Integer exprLevelLeft = expr.printLevelLeft();
            Integer exprLevelRight = expr.printLevelRight();
            // if (debug) System.err.println("Product toString(): for i=" + i + ", classOrder=" + exprClassOrder);
            // if (debug) System.err.println("Product toString(): for expr=" + expr + ", exprLevelLeft=" + exprLevelLeft + ", exprLevelRight=" + exprLevelRight);
            
            lastParens = parens;
            parens = false;
            if (i != 0 && exprLevelLeft != null && classOrder > exprLevelLeft) parens = true;
            if (i != exprs.size() - 1 && exprLevelRight != null && classOrder > exprLevelRight) parens = true;
            
            //if (exprClassOrder != null && (exprClassOrder < classOrder || (i != 0 && exprClassOrder == classOrder))) parens = true;
            //if (i == exprs.size() - 1 && expr.isFunction()) parens = false;
            
            String exprString = expr.pretty();
            
            if (i != 0) {
                if (lastMinus) {}
                else if (parens || lastParens || exprString.charAt(0) == '(') {}
                else if (lastExpr.isNumberPrinted() && (expr instanceof Var || expr instanceof Constant || expr.isFunction() || !(expr.firstAtom() instanceof Num)) && !(expr instanceof Num)) {}
                /*else if ((lastExpr instanceof Var || lastExpr instanceof Constant) && !(lastExpr instanceof Number)
                      && (    expr instanceof Var ||     expr instanceof Constant) && !(    expr instanceof Number)) {
                    string = string.concat(" ");
                }*/
                else {
                    string = string.concat("*");
                }
            }
            
            if (i == 0 && expr instanceof Num && ((Num) expr).val() == -1) {
                nowMinus = true;
                string = string.concat("-");
            }
            else {
                // if (debug) System.err.println("debug: Product.toString(): expr " + expr);
            
                string = string.concat((parens?"(":"") + exprString + (parens?")":""));
            }
            
            lastExpr = expr;
        }
        
        return string;
    }
    
    public boolean equalsExpr(Expr expr) {
        if (expr == null) return false;
        if (expr == this) return true;
        if (!(expr instanceof Product)) return false;
        
        // if (debug) System.err.println("Product: equalsExpr: " + dump() + " =? " + expr);
        ArrayList<Expr> otherExprs = ((Operation) expr).getExprs();
        for (Expr expr2 : exprs) {
            // if (debug) System.err.println("Product: equalsExpr: expr2: " + expr2);
            for (Expr otherExpr2 : otherExprs) {
                // if (debug) System.err.println("Product: equalsExpr: otherExpr2: " + otherExpr2);
                if (expr2.equalsExpr(otherExpr2)) {
                // if (debug) System.err.println("Product: equalsExpr: " + expr2 + " == " + otherExpr2);
                    otherExprs.remove(otherExpr2);
                    break;
                }
                // if (debug) System.err.println("Product: equalsExpr: " + expr2 + " != " + otherExpr2);
                return false;
            }
        }
        
        return true;
    }
    
    public Expr copyPass(HashMap<Expr, Expr> subs) {
        return make(ArrayLists.copyAll(exprs, subs));
    }
    
    public int sign() {
        int sign = 1;
        for (Expr expr : exprs) {
            if (expr.sign() == 2) return 2;
            sign*= expr.sign();
        }
        return sign;
    }
    
}
