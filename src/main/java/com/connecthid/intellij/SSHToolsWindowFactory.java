package com.connecthid.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import javax.swing.border.EmptyBorder;

public class SSHToolsWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Create menu items with icons
        DefaultListModel<MenuItem> listModel = new DefaultListModel<>();
        listModel.addElement(new MenuItem("Find", AllIcons.Actions.Find, "⌘3"));
        listModel.addElement(new MenuItem("Debug", AllIcons.Actions.StartDebugger, "⌘5"));
        listModel.addElement(new MenuItem("AI Assistant", AllIcons.Nodes.Plugin, ""));
        listModel.addElement(new MenuItem("Coverage", AllIcons.General.Modified, ""));
        listModel.addElement(new MenuItem("Hierarchy", AllIcons.Hierarchy.Class, ""));
        listModel.addElement(new MenuItem("Learn", AllIcons.Actions.Help, ""));
        listModel.addElement(new MenuItem("TODO", AllIcons.General.TodoDefault, ""));

        JBList<MenuItem> list = new JBList<>(listModel);
        list.setCellRenderer(new MenuItemRenderer());
        list.setBackground(new Color(243, 243, 243));
        list.setBorder(null);

        JBScrollPane scrollPane = new JBScrollPane(list);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        Content content = ContentFactory.getInstance().createContent(mainPanel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    // MenuItem class to hold the menu item data
    private static class MenuItem {
        final String text;
        final Icon icon;
        final String shortcut;

        MenuItem(String text, Icon icon, String shortcut) {
            this.text = text;
            this.icon = icon;
            this.shortcut = shortcut;
        }
    }

    // Custom renderer for menu items
    private static class MenuItemRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                    boolean isSelected, boolean cellHasFocus) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(new EmptyBorder(8, 12, 8, 12));
            
            if (value instanceof MenuItem item) {
                // Icon and text
                JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                leftPanel.setOpaque(false);
                JLabel iconLabel = new JLabel(item.icon);
                JLabel textLabel = new JLabel(item.text);
                leftPanel.add(iconLabel);
                leftPanel.add(textLabel);
                
                // Shortcut
                JLabel shortcutLabel = new JLabel(item.shortcut);
                shortcutLabel.setForeground(Color.GRAY);
                
                panel.add(leftPanel, BorderLayout.WEST);
                panel.add(shortcutLabel, BorderLayout.EAST);
                
                if (isSelected) {
                    panel.setBackground(new Color(209, 220, 245));
                } else {
                    panel.setBackground(list.getBackground());
                }
            }
            
            return panel;
        }
    }
} 