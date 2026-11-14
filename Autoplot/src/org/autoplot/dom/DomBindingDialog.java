package org.autoplot.dom;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.das2.qds.filters.AddFilterDialog;

/** One-to-many Autoplot DOM binding editor. Java 8. */
public final class DomBindingDialog extends JDialog {
    public interface Bridge {
        Object resolve(Component clicked, Point pointInClicked);
        void bind(Object source, String sourceProperty,
                  Object target, String targetProperty) throws Exception;
        String describe(Object node);
    }
    
    private static final String SINGLE = "Single property";
    private static final String TITLES = "Title / axis label";
    private static final String RANGE_LOG = "Range + Log";
    private static final String ALL = "All matching properties";

    private final Bridge bridge;
    private final Component canvas;
    private final Object source;
    private final JComboBox<String> mode = new JComboBox<>(
            new String[]{SINGLE, TITLES, RANGE_LOG, ALL});
    private final JComboBox<PropertyDescriptor> sourceProperty = new JComboBox<>();
    private final JPanel targetRows = new JPanel();
    private final List<TargetRow> targets = new ArrayList<>();
    private final JLabel status = new JLabel(" ");
    private final JButton pickButton = new JButton("Pick components...");
    private final JButton bindButton = new JButton("Create Bindings");
    private JRootPane pickingRoot;
    private Component oldGlass;
    private boolean oldGlassVisible;
    private PickerGlassPane picker;
    private boolean updating;

    public DomBindingDialog(Window owner, Component canvas, Object source, Bridge bridge) {
        super(owner, "Bind Component Properties", ModalityType.MODELESS);
        if (canvas == null || source == null || bridge == null)
            throw new IllegalArgumentException("canvas, source and bridge are required");
        this.canvas = canvas;
        this.source = source;
        this.bridge = bridge;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        sourceProperty.setRenderer(new DescriptorRenderer());
        fill(sourceProperty, properties(source));
        mode.addActionListener(e -> refreshMode());
        sourceProperty.addActionListener(e -> {
            if (!updating) resetTargetDefaults();
        });
        pickButton.addActionListener(e -> startPicking());
        bindButton.addActionListener(e -> createBindings());
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());

        JPanel top = new JPanel(new GridLayout(0, 2, 8, 8));
        top.add(new JLabel("Source component"));
        top.add(new JLabel(bridge.describe(source)));
        top.add(new JLabel("Property group"));
        top.add(mode);
        top.add(new JLabel("Source property"));
        top.add(sourceProperty);

        targetRows.setLayout(new BoxLayout(targetRows, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(targetRows);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.setBorder(BorderFactory.createTitledBorder("Target components and properties"));
        center.add(scroll, BorderLayout.CENTER);
        JPanel targetButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        targetButtons.add(pickButton);
        center.add(targetButtons, BorderLayout.SOUTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(close);
        buttons.add(bindButton);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(top, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(680, 450);
        setLocationRelativeTo(owner);
        refreshMode();
    }

    private static List<PropertyDescriptor> properties(Object bean) {

        String[] names;

        if (bean instanceof Axis) {
            names = new String[] {
                "range", "log", "label", "scale"
            };
        } else if (bean instanceof Plot) {
            names = new String[] {
                "title", "isotropic"
            };
        } else if (bean instanceof Annotation) {
            names = new String[] {
                "text", "background", "foreground"
            };
        } else {
            return Collections.emptyList();
        }

        List<PropertyDescriptor> result = new ArrayList<>();

        try {
            BeanInfo info = Introspector.getBeanInfo(
                    bean.getClass(), Object.class);

            // Build a lookup table of available properties.
            Map<String, PropertyDescriptor> available = new HashMap<>();

            for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
                available.put(pd.getName(), pd);
            }

            // Return only the requested properties, in the specified order.
            for (String name : names) {
                PropertyDescriptor pd = available.get(name);

                if (pd != null &&
                        pd.getReadMethod() != null &&
                        pd.getWriteMethod() != null) {
                    result.add(pd);
                }
            }

        } catch (IntrospectionException ex) {
            throw new IllegalArgumentException(
                    "Cannot inspect " + bean.getClass(), ex);
        }

        return result;
    }

    private static PropertyDescriptor find(Object bean, String name) {
        for (PropertyDescriptor pd : properties(bean))
            if (pd.getName().equals(name)) return pd;
        return null;
    }

    private static boolean compatible(PropertyDescriptor a, PropertyDescriptor b) {
        return a != null && b != null && a.getPropertyType().equals(b.getPropertyType());
    }

    private static void fill(JComboBox<PropertyDescriptor> combo, List<PropertyDescriptor> descriptors) {
        DefaultComboBoxModel<PropertyDescriptor> model = new DefaultComboBoxModel<>();
        for (PropertyDescriptor pd : descriptors) model.addElement(pd);
        combo.setModel(model);
    }

    private static void select(JComboBox<PropertyDescriptor> combo, String name) {
        combo.setSelectedIndex(-1);
        if (name == null) return;
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (name.equals(combo.getItemAt(i).getName())) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static String selectedName(JComboBox<PropertyDescriptor> combo) {
        PropertyDescriptor pd = (PropertyDescriptor) combo.getSelectedItem();
        return pd == null ? null : pd.getName();
    }

    private final class TargetRow {
        final Object node;
        final JPanel panel = new JPanel(new BorderLayout(8, 0));
        final JComboBox<PropertyDescriptor> property = new JComboBox<>();
        boolean overridden;

        TargetRow(Object node) {
            this.node = node;
            property.setRenderer(new DescriptorRenderer());
            JLabel label = new JLabel(bridge.describe(node));
            label.setPreferredSize(new Dimension(200, 28));
            panel.add(label, BorderLayout.WEST);
            panel.add(property, BorderLayout.CENTER);
            JButton remove = new JButton(new ImageIcon(AddFilterDialog.class.getResource("/resources/subtract.png")));
            remove.addActionListener(e -> {
                targets.remove(this);
                targetRows.remove(panel);
                targetRows.revalidate();
                targetRows.repaint();
                updateBindButton();
            });
            panel.add(remove, BorderLayout.EAST);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            panel.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
            property.addActionListener(e -> {
                if (!updating) {
                    overridden = true;
                    updateBindButton();
                }
            });
            updateProperties(false);
        }

        void updateProperties(boolean preserveOverride) {
            String prior = preserveOverride && overridden ? selectedName(property) : null;
            PropertyDescriptor src = (PropertyDescriptor) sourceProperty.getSelectedItem();
            List<PropertyDescriptor> matches = new ArrayList<>();
            if (src != null) {
                for (PropertyDescriptor pd : properties(node))
                    if (compatible(src, pd)) matches.add(pd);
            }
            updating = true;
            try {
                fill(property, matches);
                if (prior != null) select(property, prior);
                else select(property, src == null ? null : src.getName());
                // If the previous override became invalid, leave it unselected.
                property.setEnabled(SINGLE.equals(mode.getSelectedItem()));
            } finally {
                updating = false;
            }
        }
    }

    private void addTarget(Object node) {
        if (node == source) {
            status.setText("Source component skipped.");
            return;
        }
        for (TargetRow row : targets) {
            if (row.node == node) {
                status.setText("Already selected: " + bridge.describe(node));
                return;
            }
        }
        TargetRow row = new TargetRow(node);
        targets.add(row);
        targetRows.add(row.panel);
        targetRows.revalidate();
        targetRows.repaint();
        status.setText("Added " + bridge.describe(node) + " (" + targets.size() + " total)");
        updateBindButton();
    }

    private void resetTargetDefaults() {
        for (TargetRow row : targets) {
            row.overridden = false;
            row.updateProperties(false);
        }
        updateBindButton();
    }

    private void refreshMode() {
        boolean single = SINGLE.equals(mode.getSelectedItem());
        sourceProperty.setEnabled(single);
        for (TargetRow row : targets) row.property.setEnabled(single);
        updateBindButton();
    }

    private void updateBindButton() {
        boolean ready = !targets.isEmpty();
        if (SINGLE.equals(mode.getSelectedItem())) {
            ready &= sourceProperty.getSelectedItem() != null;
            boolean any = false;
            for (TargetRow row : targets) if (row.property.getSelectedItem() != null) any = true;
            ready &= any;
        }
        bindButton.setEnabled(ready);
    }

    private void startPicking() {
        stopPicking();
        JRootPane root = SwingUtilities.getRootPane(canvas);
        if (root == null) { status.setText("Canvas is not attached to a root pane."); return; }
        pickingRoot = root;
        oldGlass = root.getGlassPane();
        oldGlassVisible = oldGlass.isVisible();
        picker = new PickerGlassPane();
        root.setGlassPane(picker);
        picker.setVisible(true);
        picker.requestFocusInWindow();
        pickButton.setText("Picking...");
        status.setText("Click multiple components; right-click or Escape when finished.");
    }

    private void stopPicking() {
        if (picker != null) {
            picker.setVisible(false);
            if (pickingRoot != null && pickingRoot.getGlassPane() == picker) {
                pickingRoot.setGlassPane(oldGlass);
                oldGlass.setVisible(oldGlassVisible);
            }
            picker = null;
            pickingRoot = null;
            oldGlass = null;
        }
        pickButton.setText("Pick components...");
    }

    private final class PickerGlassPane extends JComponent {
        PickerGlassPane() {
            setOpaque(false);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "finish");
            getActionMap().put("finish", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { finish(); }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    e.consume();
                    if (SwingUtilities.isRightMouseButton(e)) { finish(); return; }
                    if (SwingUtilities.isLeftMouseButton(e)) pick(e.getPoint());
                }
                @Override public void mouseReleased(MouseEvent e) { e.consume(); }
                @Override public void mouseClicked(MouseEvent e) { e.consume(); }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) { e.consume(); }
                @Override public void mouseMoved(MouseEvent e) { e.consume(); }
            });
            addMouseWheelListener(e -> e.consume());
        }
        private void finish() {
            stopPicking();
            status.setText(targets.size() + " target component(s) selected.");
        }
        private void pick(Point glassPoint) {
            Point p = SwingUtilities.convertPoint(this, glassPoint, canvas);
            if (p.x < 0 || p.y < 0 || p.x >= canvas.getWidth() || p.y >= canvas.getHeight()) {
                status.setText("Click inside the canvas; right-click or Escape to finish.");
                return;
            }
            Component deepest = SwingUtilities.getDeepestComponentAt(canvas, p.x, p.y);
            if (deepest == null) deepest = canvas;
            Point local = SwingUtilities.convertPoint(canvas, p, deepest);
            try {
                Object found = bridge.resolve(deepest, local);
                if (found == null) { status.setText("No DOM component found there."); return; }
                addTarget(found);
            } catch (RuntimeException ex) {
                status.setText("Cannot resolve component: " + ex.getMessage());
            }
        }
    }

    private static final class Pair {
        final String a, b;
        Pair(String a, String b) { this.a = a; this.b = b; }
    }

    private List<Pair> bindingsFor(TargetRow row) {
        String selection = (String) mode.getSelectedItem();
        List<Pair> result = new ArrayList<>();
        Object target = row.node;
        if (SINGLE.equals(selection)) {
            PropertyDescriptor a = (PropertyDescriptor) sourceProperty.getSelectedItem();
            PropertyDescriptor b = (PropertyDescriptor) row.property.getSelectedItem();
            if (compatible(a, b)) result.add(new Pair(a.getName(), b.getName()));
        } else if (TITLES.equals(selection)) {
            PropertyDescriptor a = find(source, "title");
            if (a == null) a = find(source, "label");
            PropertyDescriptor b = find(target, "title");
            if (b == null) b = find(target, "label");
            if (compatible(a, b)) result.add(new Pair(a.getName(), b.getName()));
        } else if (RANGE_LOG.equals(selection)) {
            for (String name : new String[]{"range", "log"})
                if (compatible(find(source, name), find(target, name)))
                    result.add(new Pair(name, name));
        } else if (ALL.equals(selection)) {
            for (PropertyDescriptor a : properties(source)) {
                PropertyDescriptor b = find(target, a.getName());
                if (compatible(a, b)) result.add(new Pair(a.getName(), b.getName()));
            }
        }
        return result;
    }

    private void createBindings() {
        stopPicking();
        int succeeded = 0, failed = 0, skipped = 0;
        StringBuilder errors = new StringBuilder();
        for (TargetRow row : new ArrayList<>(targets)) {
            List<Pair> pairs = bindingsFor(row);
            if (pairs.isEmpty()) skipped++;
            for (Pair pair : pairs) {
                try {
                    bridge.bind(source, pair.a, row.node, pair.b);
                    succeeded++;
                } catch (Exception ex) {
                    failed++;
                    if (errors.length() < 1200) errors.append(bridge.describe(row.node))
                            .append(": ").append(pair.a).append(" -> ").append(pair.b)
                            .append(": ").append(ex.getMessage()).append('\n');
                }
            }
        }
        status.setText(succeeded + " binding(s) created; " + failed + " failed; " + skipped + " skipped.");
        if (failed > 0) JOptionPane.showMessageDialog(this, errors.toString(),
                "Some bindings failed", JOptionPane.WARNING_MESSAGE);
    }

    @Override public void dispose() { stopPicking(); super.dispose(); }

    private static final class DescriptorRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof PropertyDescriptor) {
                PropertyDescriptor pd = (PropertyDescriptor) value;
                setText(pd.getName() + " (" + pd.getPropertyType().getSimpleName() + ")");
            }
            return this;
        }
    }
}
