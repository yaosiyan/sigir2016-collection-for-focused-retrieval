/*
 * Copyright 2016
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.tudarmstadt.ukp.experiments.dip.wp1.documents.helpers;

import java.io.*;
import java.util.*;

/**
 * Reader for the tab-separated output from Mechanical Turk. As the output is not a standard
 * CSV file, it requires a special treatment by parsing. This class provides iterating over
 * entries as a map (column name, value)
 *
 * @author Ivan Habernal
 */
public class MTurkOutputReader
        implements Iterable<Map<String, String>>
{

    private static final boolean DEBUG = false;

    private List<Map<String, String>> records = new ArrayList<>();

    private final Set<String> columnNames = new TreeSet<>();

    private final Map<File, String> hitTypeIdForFile = new HashMap<>();

    /**
     * Loads and parses the MTurk output files
     *
     * @param additionalRequiredFields fields in records that must be present (not null)
     * @param files                    files or several files
     * @throws IOException I/O exception
     */
    public MTurkOutputReader(Set<String> additionalRequiredFields, File... files)
            throws IOException
    {
        Set<String> requiredFields = new HashSet<>(Arrays.asList("hitid", "hittypeid"));
        requiredFields.addAll(additionalRequiredFields);

        for (File file : files) {
            InputStreamReader in = new InputStreamReader(new FileInputStream(file), "utf-8");
            BufferedReader br = new BufferedReader(in);

            List<String> lines = new ArrayList<>();

            String line = br.readLine();

            StringBuilder incompleteLine = new StringBuilder();
            while (line != null) {
                String trim = line.trim();
                // this is a "correct" line, ending with [tab]"xxx" (but not [tab]")
                if (trim.endsWith("\"") && !trim.endsWith("\t\"")) {
                    incompleteLine.append(trim);
                    lines.add(incompleteLine.toString().trim());

                    incompleteLine = new StringBuilder();
                }
                else {
                    incompleteLine.append(" ").append(trim);
                }

                line = br.readLine();
            }

            if (DEBUG) {
                for (String s : lines) {
                    System.out.println("=" + s.substring(0, 20) + "..." + s
                            .substring(s.length() - 20, s.length()) + "=");
                }
            }

            String header = lines.get(0);
            SortedMap<Integer, String> headerMapping = extractHeaderMapping(header);

            columnNames.addAll(headerMapping.values());

            List<Map<String, String>> extractRecords = extractRecords(
                    lines.subList(1, lines.size()), headerMapping, requiredFields, file);

            // update hit type id for this file
            String hitTypeId = null;
            for (Map<String, String> record : extractRecords) {
                String typeId = record.get("hittypeid");
                if (hitTypeId == null) {
                    hitTypeId = typeId;
                }
                else if (!hitTypeId.equals(typeId)) {
                    System.err.println("Several hitTypeIds found in file " + file);
                }
            }
            hitTypeIdForFile.put(file, hitTypeId);

            System.out.println("Extracted " + extractRecords.size() + " records from " + file);

            records.addAll(extractRecords);
        }
    }

    /**
     * Loads and parses the MTurk output files
     *
     * @param files files or several files
     * @throws IOException I/O exception
     */
    public MTurkOutputReader(File... files)
            throws IOException

    {
        this(new HashSet<String>(), files);
    }

    /**
     * Returns a set of all column names
     *
     * @return set of strings
     */
    public Set<String> getColumnNames()
    {
        return columnNames;
    }

    /**
     * Returns extracted hittypeid for each param file
     *
     * @return map, never null
     */
    public Map<File, String> getHitTypeIdForFile()
    {
        return hitTypeIdForFile;
    }

    /**
     * Extracts the records
     *
     * @param lines         lines
     * @param headerMapping mapping from position to column name
     * @param file
     * @return list of records
     */
    private static List<Map<String, String>> extractRecords(List<String> lines,
            Map<Integer, String> headerMapping, Set<String> requiredFields, File file)
    {
        List<Map<String, String>> result = new ArrayList<>(lines.size());

        for (String line : lines) {
            List<String> stripLine = readAndStripLine(line);

            Map<String, String> record = new HashMap<>();

            for (int i = 0; i < stripLine.size(); i++) {
                String entry = stripLine.get(i).trim();

                // ignore empty records
                if (!entry.isEmpty()) {
                    String columnName = headerMapping.get(i);

                    record.put(columnName, entry);
                }
            }

            // sanity check - some fields are required
            for (String requiredField : requiredFields) {
                if (!record.keySet().contains(requiredField)) {
                    throw new IllegalStateException(
                            "Field " + requiredField + " is missing in record " + record
                                    + " in file " + file.getAbsoluteFile());
                }

                if (record.get(requiredField) == null) {
                    throw new IllegalStateException(
                            "Required field " + requiredField + " is null in file " + file
                                    .getAbsoluteFile());
                }
            }

            result.add(record);
        }

        return result;
    }

    /**
     * Extracts mapping from the first line (header) = (position, name)
     *
     * @param headerLine first line
     * @return mapping
     */
    private static SortedMap<Integer, String> extractHeaderMapping(String headerLine)
    {
        List<String> list = readAndStripLine(headerLine);

        SortedMap<Integer, String> result = new TreeMap<>();

        for (int i = 0; i < list.size(); i++) {
            result.put(i, list.get(i));
        }

        return result;
    }

    /**
     * Reads a single line, splits by tabulator, and trims the content; also removes opening anc
     * closing parentheses and normalizes double parentheses in the content
     *
     * @param line line
     * @return list of cells
     */
    private static List<String> readAndStripLine(String line)
    {
        List<String> result = new ArrayList<>();

        for (String entry : line.split("\t")) {
            result.add(
                    entry.replaceAll("^\"", "").replaceAll("\"$", "").replace("\"\"", "\"").trim());
        }

        return result;
    }

    /**
     * Iterator over the records
     *
     * @return iterator
     */
    @Override
    public Iterator<Map<String, String>> iterator()
    {
        return records.iterator();
    }
}
