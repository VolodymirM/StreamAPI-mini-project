import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class Menu {

    public static void main(String[] args) {
        new Menu();
    }

    private boolean isImported = false;
    private boolean firstUpdate = true;
    private boolean isFiltered = false;

    private int column = 0;

    private static final List<DataRecord> dataRecords = Collections.synchronizedList(new ArrayList<>());
    private List<DataRecord> filteredRecords = Collections.synchronizedList(new ArrayList<>());
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private String fromDate;
    private String toDate;

    // UI components
    private final JTable table;
    private final JTextField fromDateField;
    private final JTextField toDateField;
    private final JButton filterByDateButton;
    private final JButton sortAscButton;
    private final JButton sortDescButton;
    private final JButton loadDataButton;

    public Menu() {
        JFrame frame = new JFrame("Data Loader");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        table = new JTable(new DefaultTableModel(new String[]{"ID", "First Name", "Last Name", "Email", "Gender", "Country", "Domain Name", "Birth Date"}, 0));
        table.setDefaultEditor(Object.class, null);
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new FlowLayout());

        fromDateField = new JTextField(10);
        toDateField = new JTextField(10);
        filterByDateButton = new JButton("Filter by Date");

        sortAscButton = new JButton("Sort Ascending");
        sortDescButton = new JButton("Sort Descending");

        loadDataButton = new JButton("Load Data");

        controlsPanel.add(new JLabel("From Date (yyyy-MM-dd):"));
        controlsPanel.add(fromDateField);
        controlsPanel.add(new JLabel("To Date (yyyy-MM-dd):"));
        controlsPanel.add(toDateField);
        controlsPanel.add(filterByDateButton);
        controlsPanel.add(sortAscButton);
        controlsPanel.add(sortDescButton);
        controlsPanel.add(loadDataButton);

        panel.add(controlsPanel, BorderLayout.NORTH);
        frame.add(panel);
        frame.setVisible(true);

        // Action listeners
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                column = table.columnAtPoint(evt.getPoint());
            }
        });

        filterByDateButton.addActionListener((ActionEvent e) -> {
            if(!isImported)
                return;

            fromDate = fromDateField.getText();
            toDate = toDateField.getText();

            if (fromDate.isEmpty() && toDate.isEmpty()) {
                isFiltered = false;
                updateTable();
                return;
            }

            if (fromDate.isEmpty() && isValidDate(toDate)) {
                LocalDate to = LocalDate.parse(toDate, formatter);
                filteredRecords = new ArrayList<>(dataRecords.stream()
                    .filter(record -> {
                LocalDate recordDate = record.getBirthDate();
                return (recordDate.isEqual(to) || recordDate.isBefore(to));
                }).toList());

                isFiltered = true;
                updateTable();
                return;
            }

            if (isValidDate(fromDate) && toDate.isEmpty()) {
                LocalDate from = LocalDate.parse(fromDate, formatter);
                filteredRecords = new ArrayList<>(dataRecords.stream()
                    .filter(record -> {
                LocalDate recordDate = record.getBirthDate();
                return (recordDate.isEqual(from) || recordDate.isAfter(from));
                }).toList());

                isFiltered = true;
                updateTable();
                return;
            }

            if (isValidDate(fromDate) && isValidDate(toDate)) {
                LocalDate from = LocalDate.parse(fromDate, formatter);
                LocalDate to = LocalDate.parse(toDate, formatter);

                if (!from.isAfter(to)) {
                    filteredRecords = new ArrayList<>(dataRecords.stream()
                        .filter(record -> {
                    LocalDate recordDate = record.getBirthDate();
                    return (recordDate.isEqual(from) || recordDate.isAfter(from)) &&
                        (recordDate.isEqual(to) || recordDate.isBefore(to));
                    }).toList());

                    isFiltered = true;
                    updateTable();
                }
                else {
                    JOptionPane.showMessageDialog(null, "Wrong dates are provided.", 
                              "Error", JOptionPane.ERROR_MESSAGE);
                }

                return;
            }

            JOptionPane.showMessageDialog(null, "Invalid date format. Please enter a date in yyyy-MM-dd format.", 
                              "Error", JOptionPane.ERROR_MESSAGE);
        });

        sortAscButton.addActionListener((ActionEvent e) -> {
            if(!isImported)
                return;

            sortData(column, true);
            updateTable();
        });

        sortDescButton.addActionListener((ActionEvent e) -> {
            if(!isImported)
                return;

            sortData(column, false);
            updateTable();
        });

        loadDataButton.addActionListener((ActionEvent e) -> {
            importData();
            updateTable();
        });
    }

    private void importData() {
        if (isImported)
            return;
        isImported = true;

        String[] files = {"data/MOCK_DATA1.csv", "data/MOCK_DATA2.csv", "data/MOCK_DATA3.csv"};

        ExecutorService executorService = Executors.newFixedThreadPool(3);
        for (String file : files) {
            executorService.submit(() -> loadData(file));
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                System.err.println("Data loading threads did not finish in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Data loading was interrupted: " + e.getMessage());
        }
    }

    private static void loadData(String filePath) {
        System.out.println("Loading data form " + filePath + "...");
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] fields = line.split(",");
                if (fields.length == 8) {
                    try {
                        DataRecord record = new DataRecord(
                                Integer.parseInt(fields[0]),
                                fields[1],
                                fields[2],
                                fields[3],
                                fields[4],
                                fields[5],
                                fields[6],
                                LocalDate.parse(fields[7], formatter)
                        );
                        dataRecords.add(record);
                    } catch (NumberFormatException e) {
                        System.err.printf("Failed to parse record from file %s: %s\n", filePath, e.getMessage());
                    }
                } else {
                    System.err.printf("Incorrect format in file %s: %s\n", filePath, line);
                }
            }
            System.out.printf("Data from file %s loaded successfully.\n", filePath);
        } catch (IOException e) {
            System.err.printf("Failed to load data from file %s: %s\n", filePath, e.getMessage());
        }
    }

    private void updateTable() {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0); // Clear existing data
        
        if (firstUpdate) {
            List<DataRecord> updatedRecords = new ArrayList<>();
            int idCounter = 1;
            for (DataRecord record : dataRecords) {
                updatedRecords.add(new DataRecord(
                    idCounter++, 
                    record.getFirstName(),
                    record.getLastName(),
                    record.getEmail(),
                    record.getGender(),
                    record.getCountry(),
                    record.getDomainName(),
                    record.getBirthDate()
                ));
            }
            dataRecords.clear();
            dataRecords.addAll(updatedRecords);
            firstUpdate = false;
        }
        
        if (!isFiltered) {
            synchronized (dataRecords) {
                for (DataRecord record : dataRecords) {
                    model.addRow(new Object[]{
                            record.getId(),
                            record.getFirstName(),
                            record.getLastName(),
                            record.getEmail(),
                            record.getGender(),
                            record.getCountry(),
                            record.getDomainName(),
                            record.getBirthDate()
                    });
                }
            }
        }
        else {
            for (DataRecord record : filteredRecords) {
                model.addRow(new Object[]{
                        record.getId(),
                        record.getFirstName(),
                        record.getLastName(),
                        record.getEmail(),
                        record.getGender(),
                        record.getCountry(),
                        record.getDomainName(),
                        record.getBirthDate()
                });
            }
        }
    }

    private boolean isValidDate(String dateString) {
        try {
            LocalDate.parse(dateString, formatter);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void sortData(int column, boolean ascending) {
        Comparator<DataRecord> comparator;

        switch (column) {
            case 0 -> comparator = Comparator.comparingInt(DataRecord::getId);
            case 1 -> comparator = Comparator.comparing(DataRecord::getFirstName);
            case 2 -> comparator = Comparator.comparing(DataRecord::getLastName);
            case 3 -> comparator = Comparator.comparing(DataRecord::getEmail);
            case 4 -> comparator = Comparator.comparing(DataRecord::getGender);
            case 5 -> comparator = Comparator.comparing(DataRecord::getCountry);
            case 6 -> comparator = Comparator.comparing(DataRecord::getDomainName);
            case 7 -> comparator = Comparator.comparing(DataRecord::getBirthDate);
            default -> throw new IllegalArgumentException("Invalid column: " + column);
        }

        if (!ascending) {
            comparator = comparator.reversed();
        }

        if (isFiltered) {
            synchronized (filteredRecords) {
                filteredRecords.sort(comparator);
            }
        } else {
            synchronized (dataRecords) {
                dataRecords.sort(comparator);
            }
        }
    }

}
