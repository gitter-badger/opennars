/*
 * Created on 13-Mar-08
 */
package ca.nengo.model.impl;

import ca.nengo.dynamics.impl.SimpleLTISystem;
import ca.nengo.model.*;
import ca.nengo.util.MU;
import ca.nengo.util.ScriptGenException;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unit tests for EnsembleImpl.
 *
 * @author Bryan Tripp
 */
public class GroupImplTest extends TestCase {

	@Override
    protected void setUp() throws Exception {
		super.setUp();
	}

	public void testClone() throws StructuralException, CloneNotSupportedException {
        System.out.println("EnsembleImplTest");
		MockExpandableNode node1 = new MockExpandableNode("1", new NSource[0],
				new NTarget[]{new BasicTarget(null, new SimpleLTISystem(1, 1, 1), null, "existing")});
		MockExpandableNode node2 = new MockExpandableNode("2", new NSource[0],
				new NTarget[]{new BasicTarget(null, new SimpleLTISystem(1, 1, 1), null, "existing")});
		GroupImpl ensemble = new GroupImpl("ensemble", new Node[]{node1, node2});
		ensemble.addTarget("new", MU.uniform(2, 2, 1), .005f, false);

		GroupImpl copy = ensemble.clone();
        System.out.println("Termination Length");
        System.out.println( copy.getTargets().length);
		assertEquals(2, copy.getTargets().length);
		copy.removeTarget("new");
        System.out.println("Termination Name");
        System.out.println( copy.getTarget("existing").getClass().getName());
		assertTrue(copy.getTarget("existing") instanceof GroupTarget);
//		try {
//			copy.removeTermination("existing");
//			fail("Should have thrown exception (can't remove non-expanded terminations)");
//		} catch (StructuralException e) {
//			e.printStackTrace();
//		}
	}

	public class MockExpandableNode extends AbstractNode implements ExpandableNode {

		private static final long serialVersionUID = 1L;

		private final Map<String, NTarget> myExpandedTerminations;

		public MockExpandableNode(String name, NSource[] sources, NTarget[] targets) {
			super(name, Arrays.asList(sources), Arrays.asList(targets));
			myExpandedTerminations = new LinkedHashMap<String, NTarget>(10);
		}

		public NTarget addTarget(String name, float[][] weights, float tauPSC, boolean modulatory) throws StructuralException {
			NTarget result = new BasicTarget(this, new SimpleLTISystem(1, 1, 1), null, name);
			myExpandedTerminations.put(name, result);
			return result;
		}

		public int getDimension() {
			return 1;
		}

		public NTarget removeTarget(String name) throws StructuralException {
			return myExpandedTerminations.remove(name);
		}

		@Override
		public NTarget getTarget(String name) throws StructuralException {
			if (myExpandedTerminations.containsKey(name)) {
				return myExpandedTerminations.get(name);
			} else {
				return super.getTarget(name);
			}
		}

		@Override
		public NTarget[] getTargets() {
			NTarget[] result = new NTarget[super.getTargets().length + myExpandedTerminations.size()];
			int i = 0;
			for (NTarget t : myExpandedTerminations.values()) {
				result[i++] = t;
			}
			System.arraycopy(super.getTargets(), 0, result, i++, super.getTargets().length);

			return result;
		}

		@Override
		public void run(float startTime, float endTime) {
        System.out.println("EnsembleImplTestRun");	}

		@Override
		public void reset(boolean randomize) {}

		public Node[] getChildren() {
			// TODO Auto-generated method stub
			return null;
		}

		public String toScript(HashMap<String, Object> scriptData) throws ScriptGenException {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public MockExpandableNode clone() throws CloneNotSupportedException {
			return (MockExpandableNode) super.clone();
		}

	}

}
