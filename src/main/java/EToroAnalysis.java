import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EToroAnalysis {

    static EToroAnalysis instance = null;

    public static EToroAnalysis getInstance() {
        if (instance == null) {
            return new EToroAnalysis();
        }
        return instance;
    }

    public static void main(String[] args) throws Exception {
        EToroAnalysis.getInstance().calculateShares();
    }

    public void calculateShares() throws Exception {
        // Enter your xml file here
        FileInputStream inputStream = new FileInputStream(
"");

        // Get the workbook instance for XLS file
        XSSFWorkbook workbook = new XSSFWorkbook(inputStream);

        // Get first sheet from the workbook
        XSSFSheet sheet = workbook.getSheetAt(1);

        // Get iterator to all the rows in current sheet
        Iterator<Row> rowIterator = sheet.iterator();

        HashMap<String, Double> amountMap = new HashMap();
        HashMap<String, Double> profitMap = new HashMap();
        HashMap<String, Double> percentMap = new HashMap();
        HashMap<String, Long> timeDifMap = new HashMap();
        HashMap<String, Double> profitPerTime = new HashMap();
        HashMap<String, Integer> entryCounterMap = new HashMap();
        HashMap<String, Double> profitPerTimePer1KEuro = new HashMap();

        LocalDateTime tempLocalDate;
        String action;
        Row row;
        Cell cell;
        goToB1(rowIterator);
        Iterator<Cell> cellIterator;
        double profit;
        long tempTime;
        while (rowIterator.hasNext()) {
            row = rowIterator.next();
            cellIterator = row.cellIterator();
            cellIterator.next();
            cell = cellIterator.next();

            // next cell value contains "Buy"?
            if (cell.getStringCellValue().contains("Buy")) {
                action = cell.getStringCellValue();
                // set profitMap default
                if (!profitMap.containsKey(cell.getStringCellValue())) {
                    setDefaultValues(amountMap, profitMap, percentMap, timeDifMap, profitPerTime, entryCounterMap, action);
                }
                cell = cellIterator.next();
                amountMap.put(action, amountMap.get(action)+Double.parseDouble(cell.getStringCellValue().replace(",", ""))/100);
                cell = goToNextProfit(cellIterator);

                profit = Double.parseDouble(cell.getStringCellValue().replace(",", ""))/100;
                profitMap.put(action, profitMap.get(action)+profit);

                cell = cellIterator.next();

                tempLocalDate = LocalDateTime.parse(cell.getStringCellValue(), DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

                cell = cellIterator.next();

                Date tempDate = convertToDateViaSqlTimestamp(tempLocalDate);

                tempTime = (convertToDateViaSqlTimestamp(LocalDateTime.parse(cell.getStringCellValue(),
                        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).getTime() - tempDate.getTime());

                if (tempTime == 0) {
                    timeDifMap.put(action, timeDifMap.get(action) + 60000); //add 1min if open and close date < 1min difference
                } else {
                    timeDifMap.put(action, timeDifMap.get(action) + tempTime);
                }

                entryCounterMap.put(action, entryCounterMap.get(action) + 1);
            }
        }

        profitMap.forEach((k, v) -> {
            profitPerTime.put(k, v/timeDifMap.get(k)*(1000 * 60 * 60 * 24));
            profitPerTimePer1KEuro.put(k, profitPerTime.get(k) / amountMap.get(k) * 1000);
        });

        sortByValue(profitPerTimePer1KEuro).forEach((k, v) -> {
            double totalHours = Double.parseDouble(String.valueOf(timeDifMap.get(k))) / (1000 * 60 * 60);
            System.out.println("\nAction: " + k);
            System.out.println("Amount: " + amountMap.get(k));
            System.out.println("Profit: " + profitMap.get(k));
            System.out.println("Percent: " + v/amountMap.get(k)*100);
            System.out.println("Total hours: " + totalHours);
            System.out.println("Profit / day: " + profitPerTime.get(k));
            System.out.println("Profit / day / 1000€: " + profitPerTimePer1KEuro.get(k));
        });

        filterValues(profitPerTimePer1KEuro, profitMap);
    }

    private Cell goToNextProfit(Iterator<Cell> cellIterator) {
        Cell cell;
        cellIterator.next();
        cellIterator.next();
        cellIterator.next();
        cellIterator.next();
        cell = cellIterator.next();
        return cell;
    }

    private void goToB1(Iterator<Row> rowIterator) {
        Row row;
        row = rowIterator.next();
        Iterator<Cell> cellIterator = row.cellIterator();
        cellIterator.next();
        cellIterator.next();
    }

    private void setDefaultValues(HashMap<String, Double> amountMap, HashMap<String, Double> profitMap, HashMap<String, Double> percentMap, HashMap<String, Long> timeDifMap, HashMap<String, Double> profitPerTime, HashMap<String, Integer> entryCounterMap, String currentValue) {
        profitMap.put(currentValue, 0.);
        amountMap.put(currentValue, 0.);
        percentMap.put(currentValue, 0.);
        profitPerTime.put(currentValue, 0.);
        timeDifMap.put(currentValue, (long) 0);
        entryCounterMap.put(currentValue, 0);
    }

    public Date convertToDateViaSqlTimestamp(LocalDateTime dateToConvert) {
        return java.sql.Timestamp.valueOf(dateToConvert);
    }

    private static Map<String, Double> sortByValue(Map<String, Double> unsortedMap) {
        // 1. Convert Map to List of Map
        List<Map.Entry<String, Double>> list =
                new LinkedList<>(unsortedMap.entrySet());

        // 2. Sort list with Collections.sort(), provide a custom Comparator
        //    Try switch the o1 o2 position for a different order
        Collections.sort(list, Comparator.comparing(Map.Entry::getValue));

        // 3. Loop the sorted list and put it into a new insertion order Map LinkedHashMap
        Map<String, Double> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

    private void filterValues(Map<String, Double> profitPerTimePer1KEuro, HashMap<String, Double> profitMap) {
        HashMap<String, Double> filteredValuesProfitPerTimePer1KEuro = new HashMap();
        double sumProfitPerTimePer1KEuro = 0.;

        profitPerTimePer1KEuro.entrySet().stream().filter(e -> e.getValue() > 0)
                .forEach(e -> filteredValuesProfitPerTimePer1KEuro.put(e.getKey(), e.getValue()));
        for (Double v: filteredValuesProfitPerTimePer1KEuro.values()) {
            sumProfitPerTimePer1KEuro+= v;
        }
        System.out.println();
        System.out.println("sumProfitPerTimePer1KEuro: " + sumProfitPerTimePer1KEuro);

        genInvRecommendation(filteredValuesProfitPerTimePer1KEuro, profitMap, 1748);
    }

    private void genInvRecommendation(Map<String, Double> filteredValuesProfitPerTimePer1KEuro,
                                      HashMap<String, Double> profit, int budget) {
        LinkedList<String> keysFilteredByProfitability = new LinkedList<>();
        LinkedList<Double> valuesFilteredByProfitability = new LinkedList<>();
        double invSum = 0.;

        profit = (HashMap<String, Double>) sortByValue(profit);
        filteredValuesProfitPerTimePer1KEuro = sortByValue(filteredValuesProfitPerTimePer1KEuro);
        for (String k : profit.keySet()) {
            //check if at least 25$ profit were made
            if ((profit.get(k) - 25) > 0) {
                keysFilteredByProfitability.add(k);
                valuesFilteredByProfitability.add(filteredValuesProfitPerTimePer1KEuro.get(k));
            }
        }

        for (int i = 0; i < valuesFilteredByProfitability.size(); i++) {
            double currentInvRec = profit.get(keysFilteredByProfitability.get(i)) * 2;

            if (currentInvRec < budget) {
                System.out.println(keysFilteredByProfitability.get(i) + ": " + currentInvRec);
                System.out.println("Save Loss: " + (currentInvRec - profit.get(keysFilteredByProfitability.get(i))));
                budget -= currentInvRec;
                invSum = invSum + currentInvRec;
            }
        }
        System.out.println("Total Investment: " + invSum);
        System.out.println();
    }
}