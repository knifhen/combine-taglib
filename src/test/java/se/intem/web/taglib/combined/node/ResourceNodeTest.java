package se.intem.web.taglib.combined.node;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.Arrays;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

public class ResourceNodeTest {

    private ResourceNode a;
    private ResourceNode b;
    private ResourceNode c;
    private ResourceNode d;
    private ResourceNode e;

    @Before
    public void setup() {
        this.a = new ResourceNode("a");
        this.b = new ResourceNode("b");
        this.c = new ResourceNode("c");
        this.d = new ResourceNode("d");
        this.e = new ResourceNode("e");

        a.addEdges(b, d);
        b.addEdges(c, e);
        c.addEdges(d, e);
    }

    @Test
    public void leaf_depends_only_on_itself() {
        assertThat(e.resolve(), Matchers.is(Arrays.asList(e)));
    }

    @Test
    public void resolved_list_should_contain_unique_nodes() {
        assertThat(a.resolve(), Matchers.is(Arrays.asList(d, e, c, b, a)));
    }

    @Test
    public void should_detect_circular_dependency() {

        d.addEdges(b);

        try {
            a.resolve();
            fail("Should have thrown exception");
        } catch (IllegalStateException e) {
            assertEquals("Circular dependency detected: d -> b", e.getMessage());
        }

    }

    @Test
    public void virtual_node_with_no_edges_has_no_dependencies() {
        ResourceNode node = new ResourceNode("virtual");
        node.setVirtual(true);

        assertTrue(node.resolve().isEmpty());
    }

    @Test
    public void tree_of_virtual_nodes() {
        ResourceNode virtual2 = new ResourceNode().addEdges(d, e).setVirtual(true);
        ResourceNode virtual1 = new ResourceNode().addEdges(virtual2, e).setVirtual(true);

        assertThat(virtual1.resolve(), Matchers.is(Arrays.asList(d, e)));
    }

    @Test
    public void dependency_on_self_should_be_trivially_satisfied() {
        e.addEdges(e);
        assertThat(e.resolve(), is(Arrays.asList(e)));

        a.addEdges(a);
        b.addEdges(b);
        c.addEdges(c);
        d.addEdges(d);
        e.addEdges(e);

        assertThat(a.resolve(), is(Arrays.asList(d, e, c, b, a)));
    }

    @Test
    public void do_not_add_self_as_satifies() {
        assertEquals(0, a.getSatisfies().size());
        a.addSatisfies(a);
        assertEquals(0, a.getSatisfies().size());
    }

    @Test(expected = IllegalStateException.class)
    public void do_not_allow_adding_satisfies_to_root() {
        ConfigurationItem item = new ConfigurationItem();
        item.setRoot(true);
        ResourceNode node = new ResourceNode("root", item);
        node.addSatisfies(a);
    }

}
