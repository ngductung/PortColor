package org.example;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.HighlightColor;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.*;

public class PortColor implements BurpExtension {

    private final Map<Integer, HighlightColor> colorRules = new HashMap<>();
    private final SortedSet<Integer> discoveredPorts = new TreeSet<>();
    private DefaultTableModel tableModel;
    private JComboBox<Integer> portDropdown;
    private MontoyaApi api;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("Port Color");

        // 1. TẢI CẤU HÌNH (Sử dụng getString)
        loadConfig();

        // --- GIAO DIỆN (UI) ---
        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        portDropdown = new JComboBox<>();
        portDropdown.setPreferredSize(new Dimension(100, 25));

        // Thêm các port đã load vào dropdown
        for (Integer p : discoveredPorts) {
            portDropdown.addItem(p);
        }

        JComboBox<HighlightColor> colorCombo = new JComboBox<>(HighlightColor.values());
        colorCombo.removeItem(HighlightColor.NONE);

        JButton btnAdd = new JButton("Add Rule");
        JButton btnDelete = new JButton("Delete Selected");
        JButton btnClear = new JButton("Clear All");

        controlPanel.add(new JLabel("Detected Port:"));
        controlPanel.add(portDropdown);
        controlPanel.add(new JLabel("Color:"));
        controlPanel.add(colorCombo);
        controlPanel.add(btnAdd);
        controlPanel.add(Box.createHorizontalStrut(30));
        controlPanel.add(btnDelete);
        controlPanel.add(btnClear);

        String[] columns = {"Listener Port", "Highlight Color"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(tableModel);
        table.setRowHeight(25);
        refreshTable();

        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        // --- SỰ KIỆN ---
        btnAdd.addActionListener(e -> {
            Integer selectedPort = (Integer) portDropdown.getSelectedItem();
            if (selectedPort != null) {
                colorRules.put(selectedPort, (HighlightColor) colorCombo.getSelectedItem());
                saveConfig();
                refreshTable();
            }
        });

        btnDelete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                colorRules.remove((Integer) tableModel.getValueAt(row, 0));
                saveConfig();
                refreshTable();
            }
        });

        btnClear.addActionListener(e -> {
            if (!colorRules.isEmpty()) {
                colorRules.clear();
                saveConfig();
                refreshTable();
            }
        });

        api.userInterface().registerSuiteTab("PortColor", mainPanel);

        // --- PROXY LOGIC ---
        api.proxy().registerRequestHandler(new ProxyRequestHandler() {
            @Override
            public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest) {
                int port = getPortFromListenerInterface(interceptedRequest);
                if (port != -1) {
                    if (!discoveredPorts.contains(port)) {
                        discoveredPorts.add(port);
                        updateDropdown();
                        saveConfig();
                    }
                    if (colorRules.containsKey(port)) {
                        interceptedRequest.annotations().setHighlightColor(colorRules.get(port));
                    }
                }
                return ProxyRequestReceivedAction.continueWith(interceptedRequest);
            }

            @Override
            public ProxyRequestToBeSentAction handleRequestToBeSent(InterceptedRequest interceptedRequest) {
                return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
            }
        });
    }

    // --- SỬA LỖI: Dùng setString thay cho saveString ---
    private void saveConfig() {
        StringBuilder sb = new StringBuilder();
        colorRules.forEach((port, color) -> sb.append(port).append(":").append(color.name()).append(","));
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);

        sb.append("|");

        discoveredPorts.forEach(port -> sb.append(port).append(","));
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);

        // Montoya API dùng setString
        api.persistence().extensionData().setString("port_color_config", sb.toString());
    }

    // --- SỬA LỖI: Dùng getString thay cho loadString ---
    private void loadConfig() {
        // Montoya API dùng getString
        String data = api.persistence().extensionData().getString("port_color_config");
        if (data == null || data.isEmpty()) return;

        try {
            String[] parts = data.split("\\|", -1);
            if (parts.length > 0 && !parts[0].isEmpty()) {
                for (String rule : parts[0].split(",")) {
                    String[] kv = rule.split(":");
                    if (kv.length == 2) {
                        colorRules.put(Integer.parseInt(kv[0]), HighlightColor.valueOf(kv[1]));
                    }
                }
            }
            if (parts.length > 1 && !parts[1].isEmpty()) {
                for (String p : parts[1].split(",")) {
                    discoveredPorts.add(Integer.parseInt(p));
                }
            }
        } catch (Exception e) {
            api.logging().logToError("Error loading config: " + e.getMessage());
        }
    }

    private void updateDropdown() {
        SwingUtilities.invokeLater(() -> {
            Integer current = (Integer) portDropdown.getSelectedItem();
            portDropdown.removeAllItems();
            for (Integer p : discoveredPorts) {
                portDropdown.addItem(p);
            }
            if (current != null) portDropdown.setSelectedItem(current);
        });
    }

    private void refreshTable() {
        if (tableModel == null) return;
        tableModel.setRowCount(0);
        colorRules.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> tableModel.addRow(new Object[]{entry.getKey(), entry.getValue()}));
    }

    private int getPortFromListenerInterface(Object requestObject) {
        try {
            Method m = requestObject.getClass().getMethod("listenerInterface");
            Object result = m.invoke(requestObject);
            if (result != null) {
                String s = result.toString();
                if (s.contains(":")) return Integer.parseInt(s.substring(s.lastIndexOf(":") + 1));
            }
        } catch (Exception e) {
            try {
                Method m2 = requestObject.getClass().getMethod("getListenerInterface");
                Object result2 = m2.invoke(requestObject);
                if (result2 != null) {
                    String s = result2.toString();
                    if (s.contains(":")) return Integer.parseInt(s.substring(s.lastIndexOf(":") + 1));
                }
            } catch (Exception ex) { }
        }
        return -1;
    }
}