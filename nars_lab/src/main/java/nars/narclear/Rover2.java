package nars.narclear;

import nars.core.Memory;
import nars.core.NAR;
import nars.core.Parameters;
import nars.core.build.Default;
import nars.core.build.Neuromorphic;
import nars.gui.NARSwing;
import nars.logic.entity.Task;
import nars.logic.entity.Term;
import nars.logic.nal8.Operation;
import nars.narclear.jbox2d.TestbedPanel;
import nars.narclear.jbox2d.TestbedSettings;
import nars.narclear.jbox2d.j2d.DrawPhy2D;
import nars.operator.NullOperator;
import org.jbox2d.common.MathUtils;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * NARS Rover
 *
 * @author me
 */
public class Rover2 extends PhysicsModel {


    float curiosity = 0.05f;

    /* how often to input mission, in frames */
    int missionPeriod = 100;

    boolean wraparound = false;

    public RoverModel rover;
    final NAR nar;
    int mission = 0;


    public static void main(String[] args) {
        Parameters.DEBUG = true;
        boolean multithread = true;

        NARSwing.themeInvert();

        NAR nar;
        if (multithread) {
            Parameters.THREADS = 4;
            nar = new NAR(new Neuromorphic(128).simulationTime()
                    .setConceptBagSize(1500).setSubconceptBagSize(4000)
                    .setNovelTaskBagSize(512)
                    .setTermLinkBagSize(150)
                    .setTaskLinkBagSize(60)
                    .setInternalExperience(null));
            nar.setCyclesPerFrame(256);
        }
        else {
            Parameters.THREADS = 1;
            nar = new NAR(new Default().simulationTime());
            nar.setCyclesPerFrame(8);
        }

        //NAR nar = new CurveBagNARBuilder().

        //NAR nar = new Discretinuous().temporalPlanner(8, 64, 16).


        //new NARPrologMirror(nar, 0.30f, true, true, false);



        float framesPerSecond = 30f;

        Parameters.STM_SIZE = 4;
        (nar.param).noiseLevel.set(3);
        (nar.param).duration.set(5);
        (nar.param).conceptForgetDurations.set(25f);
        (nar.param).taskLinkForgetDurations.set(25f);
        (nar.param).termLinkForgetDurations.set(25f);
        (nar.param).novelTaskForgetDurations.set(20f);

        final Rover2 theRover = new Rover2(nar);

        //new NARPrologMirror(nar,0.75f, true).temporal(true, true);
        //ItemCounter removedConcepts = new ItemCounter(nar, Events.ConceptForget.class);
        // RoverWorld.world= new RoverWorld(rv, 48, 48);
        new NARPhysics<Rover2>(nar, 1.0f / framesPerSecond, theRover ) {

            @Override
            public void cycle() {
                super.cycle();
                nar.memory.addSimulationTime(1);
            }


            @Override
            public void keyPressed(KeyEvent e) {

                if (e.getKeyChar() == 'm') {
                    theRover.mission = (theRover.mission + 1) % 2;
                    System.out.println("Mission: " + theRover.mission);
                } else if (e.getKeyChar() == 'g') {
                    System.out.println(nar.memory.concepts);
                    //removedConcepts.report(System.out);
                }

//                if (e.getKeyCode() == KeyEvent.VK_UP) {
//                    if(!Rover2.allow_imitate) {
//                        nar.addInput("(^motor,linear,1). :|:");
//                    } else {
//                        nar.addInput("(^motor,linear,1)!");
//                    }
//                }
//                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
//                    if(!Rover2.allow_imitate) {
//                        nar.addInput("(^motor,linear,-1). :|:");
//                    } else {
//                        nar.addInput("(^motor,linear,-1)!");
//                    }
//                }
//                if (e.getKeyCode() == KeyEvent.VK_LEFT) {
//                    if(!Rover2.allow_imitate) {
//                        nar.addInput("(^motor,turn,-1). :|:");
//                    } else {
//                        nar.addInput("(^motor,turn,-1)!");
//                    }
//                }
//                if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
//                    if(!Rover2.allow_imitate) {
//                        nar.addInput("(^motor,turn,1). :|:");
//                    } else {
//                        nar.addInput("(^motor,turn,1)!");
//                    }
//                }
            }

        };

        nar.start((long)(1000f/framesPerSecond));

        // new NWindow("Tasks",new TaskTree(nar)).show(300,600);
    }




    private static final double TWO_PI = 2 * Math.PI;

    public static double normalizeAngle(final double theta) {
        double normalized = theta % TWO_PI;
        normalized = (normalized + TWO_PI) % TWO_PI;
        if (normalized > Math.PI) {
            normalized -= TWO_PI;
        }
        return normalized;
    }

    final int angleResolution = 8;
    int cnt = 0;

    public String angleTerm(final float a) {
        float h = (float) normalizeAngle(a);
        h /= MathUtils.PI;
        int i = (int) (h * angleResolution / 2f);
        String t = "a" + i;

        if (i == 0) {
            t = "forward";
        } else if (i == angleResolution / 4) {
            t = "left";
        } else if (i == -angleResolution / 4) {
            t = "right";
        } else if ((i == (angleResolution / 2 - 1)) || (i == -(angleResolution / 2 - 1))) {
            t = "reverse";
        }

        return t;
    }

    /**
     * maps a value (which must be in range 0..1.0) to a term name
     */
    public static String f(double p) {
        if (p < 0) {
            throw new RuntimeException("Invalid value for: " + p);
            //p = 0;
        }
        if (p > 0.99f) {
            p = 0.99f;
        }
        int i = (int) (p * 10f);
        switch (i) {
            case 9:
            case 8:
            case 7:
                return "xxxx";
            case 6:
            case 5:
                return "xxx";
            case 4:
            case 3:
                return "xx";
            case 2:
            case 1:
                return "x";
            default:
                return "0";
        }
    }

    public static enum Material implements DrawPhy2D.DrawProperty {

        Food, Wall, Block;

        static final Color foodStroke = new Color(0.25f, 1f, 0.25f);
        static final Color foodFill = new Color(0.15f, 0.9f, 0.15f);

        static final Color wallStroke = new Color(0.25f, 0.25f, 0.25f);
        static final Color wallFill = new Color(0.5f, 0.5f, 0.5f);

        @Override
        public void before(Body b, DrawPhy2D d) {
            switch (this) {
                case Food:
                    d.setStrokeColor(foodStroke);
                    d.setFillColor(foodFill);
                    break;
                case Wall:
                    d.setStrokeColor(wallStroke);
                    d.setFillColor(wallFill);
                    break;
            }
        }
    };

    public Rover2(NAR nar) {
        this.nar = nar;
    }


    protected void thrustRelative(float f) {
        if (f == 0) {
            rover.torso.setLinearVelocity(new Vec2());
        } else {
            rover.thrust(0, f * linearThrustPerCycle);
        }
    }

    protected void rotateRelative(float f) {
        rover.rotate(f * angularSpeedPerCycle);
    }

    protected void addAxioms() {

        nar.addInput("<{left,right,forward,reverse} --> direction>.");
        nar.addInput("<{Wall,Empty,Food} --> material>.");
        nar.addInput("<{0,x,xx,xxx,xxxx} --> magnitude>.");

        nar.addInput("<0 <-> x>. %0.60;0.60%");
        nar.addInput("<x <-> xx>. %0.60;0.60%");
        nar.addInput("<xx <-> xxx>. %0.60;0.60%");
        nar.addInput("<xxx <-> xxxx>. %0.60;0.60%");
        nar.addInput("<0 <-> xxxx>. %0.00;0.95%");

    }

    protected void inputMission() {

        addAxioms();

        if (mission == 0) {
            //seek food  
            curiosity = 0.05f;
            nar.addInput("<goal --> Food>! %1.00;0.99%");
            nar.addInput("<goal --> stop>! %0.00;0.99%");
            //nar.addInput("Wall! %0.00;0.50%");            
            nar.addInput("<goal --> feel>! %1.00;0.70%");
        } else if (mission == 1) {
            //rest
            curiosity = 0;
            nar.addInput("<goal --> stop>! %1.00;0.99%");
            nar.addInput("<goal --> Food>! %0.00;0.99%");
        }
        //..
    }

    @Override
    public void step(float timeStep, TestbedSettings settings, TestbedPanel panel) {
        cnt++;

        super.step(timeStep, settings, panel);

        rover.step();

    }

    public class RoverPanel extends JPanel {

        public class InputButton extends JButton implements ActionListener {

            private final String command;

            public InputButton(String label, String command) {
                super(label);
                addActionListener(this);
                //this.addKeyListener(this);
                this.command = command;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                nar.addInput(command);
            }

        }

        public RoverPanel(RoverModel rover) {
            super(new BorderLayout());

            {
                JPanel motorPanel = new JPanel(new GridLayout(0, 2));

//                motorPanel.add(new InputButton("Stop", "(^motor,stop). :|:"));
//                motorPanel.add(new InputButton("Forward", "(^motor,forward). :|:"));
//                motorPanel.add(new InputButton("TurnLeft", "(^motor,turn,left). :|:"));
//                motorPanel.add(new InputButton("TurnRight", "(^motor,turn,right). :|:"));
//                motorPanel.add(new InputButton("Backward", "(^motor,backward). :|:"));
                add(motorPanel, BorderLayout.SOUTH);
            }
        }

    }


    public RoverWorld world;
    public static int sz = 48;

    @Override
    public void initTest(boolean deserialized) {
        getWorld().setGravity(new Vec2());
        getWorld().setAllowSleep(false);

        world = new FoodSpawnWorld1(this, sz, sz);
        //world = new GridSpaceWorld(this, GridSpaceWorld.newMazePlanet());

        rover = new RoverModel(this, this);

        //new NWindow("Rover Control", new RoverPanel(rover)).show(300, 200);
        addAxioms();
        addOperators();

        randomAction();
    }

    public float linearThrustPerCycle = 15f;
    public float angularSpeedPerCycle = 0.24f;

    public static boolean allow_imitate = true;

    static final ArrayList<String> randomActions = new ArrayList<>();

    static {
        String p = "$0.99;0.75;0.90$ ";
        randomActions.add("(^motor,left)!");
        randomActions.add(p + "(^motor,left,left)!");
        randomActions.add("(^motor,right)!");
        randomActions.add(p + "(^motor,right,right)!");
        //randomActions.add("(^motor,forward,forward)!"); //too much actions are not good, 
        randomActions.add(p + "(^motor,forward)!"); //however i would agree if <(^motor,forward,forward) --> (^motor,forward)>.
        //randomActions.add("(^motor,forward,forward)!");
        randomActions.add(p + "(^motor,forward)!");
        //randomActions.add("(^motor,reverse)!");
        randomActions.add(p + "(^motor,stop)!");
        //randomActions.add("(^motor,random)!");
    }

    protected void randomAction() {
        int candid = (int) (Math.random() * randomActions.size());
        nar.addInput(randomActions.get(candid));
    }

    protected void addOperators() {
        nar.addPlugin(new NullOperator("^motor") {

            @Override
            protected List<Task> execute(Operation operation, Term[] args, Memory memory) {

                Term t1 = args[0];

                float priority = operation.getTask().budget.getPriority();

                String command = "";
                if (args.length == 1+1) {
                    command = t1.name().toString();
                }
                if (args.length == 2+1) {
                    Term t2 = args[1];
                    command = t1.name().toString() + "," + t2.name().toString();
                } else if (args.length == 3+1) {
                    Term t2 = args[1];
                    Term t3 = args[2];
                    command = t1.name().toString() + "," + t2.name().toString() + "," + t3.name().toString();
                }

                switch (command) {
                    case "right":
                        rotateRelative(-10);
                        break;
                    case "right,right":
                        rotateRelative(-20);
                        break;
                    case "left":
                        rotateRelative(10);
                        break;
                    case "left,left":
                        rotateRelative(20);
                        break;
                    case "forward,forward":
                        thrustRelative(3);
                        break;
                    case "forward":
                        thrustRelative(1);
                        break;
                    case "reverse":
                        thrustRelative(-1);
                        break;
                    case "stop":
                        rover.stop();
                        break;
                    case "random":
                        randomAction();
                        break;
                }

                return null;
            }
        });

    }


    @Override     public String getTestName() {
        return "NARS Rover";
    }

}