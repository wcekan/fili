// Copyright 2017 Yahoo Inc.
// Licensed under the terms of the Apache license. Please see LICENSE.md file distributed with this work for terms.
package com.yahoo.bard.webservice.table;

import com.yahoo.bard.webservice.data.dimension.DimensionColumn;
import com.yahoo.bard.webservice.data.metric.MetricColumn;
import com.yahoo.bard.webservice.data.time.ZonedTimeGrain;

import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.Interval;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

/**
 * The schema for a physical table.
 */
public class PhysicalTableSchema extends BaseSchema implements Schema {

    private final ZonedTimeGrain timeGrain;
    private final Map<String, String> logicalToPhysicalColumnNames;
    private final Map<String, Set<String>> physicalToLogicalColumnNames;

    /**
     * Constructor.
     *
     * @param timeGrain The time grain for this table
     * @param columns The columns for this table
     * @param logicalToPhysicalColumnNames The mapping of logical column names to physical names
     */
    public PhysicalTableSchema(
            @NotNull ZonedTimeGrain timeGrain,
            Iterable<Column> columns,
            @NotNull Map<String, String> logicalToPhysicalColumnNames
    ) {
        super(timeGrain, columns);
        this.timeGrain = timeGrain;

        this.logicalToPhysicalColumnNames = Collections.unmodifiableMap(logicalToPhysicalColumnNames);
        this.physicalToLogicalColumnNames = Collections.unmodifiableMap(
                this.logicalToPhysicalColumnNames.entrySet().stream().collect(
                        Collectors.groupingBy(
                                Map.Entry::getValue,
                                Collectors.mapping(Map.Entry::getKey, Collectors.toSet())
                        )
                )
        );
    }

    /**
     * Translate a logical name into a physical column name. If no translation exists (i.e. they are the same),
     * then the logical name is returned.
     * <p>
     * NOTE: This defaulting behavior <em>WILL BE REMOVED</em> in future releases.
     * <p>
     * The defaulting behavior shouldn't be hit for Dimensions that are serialized via the default serializer and are
     * not properly configured with a logical-to-physical name mapping. Dimensions that are not "normal" dimensions,
     * such as dimensions used for DimensionSpecs in queries to do mapping from fact-level dimensions to something else,
     * should likely use their own serialization strategy so as to not hit this defaulting behavior.
     *
     * @param logicalName  Logical name to lookup in physical table
     *
     * @return Translated logicalName if applicable
     */
    public String getPhysicalColumnName(String logicalName) {
        return logicalToPhysicalColumnNames.getOrDefault(logicalName, logicalName);
    }

    /**
     * Look up all the logical column names corresponding to a physical name.
     * If no translation exists (i.e. they are the same), then the physical name is returned.
     *
     * @param physicalName  Physical name to lookup in physical table
     *
     * @return Translated physicalName if applicable
     */
    public Set<String> getLogicalColumnNames(String physicalName) {
        return physicalToLogicalColumnNames.getOrDefault(physicalName, Collections.singleton(physicalName));
    }

    /**
     * Returns true if the mapping of names is populated for this logical name.
     *
     * @param logicalName the name of a metric or dimension column
     *
     * @return true if this table supports this column explicitly
     */
    public boolean containsLogicalName(String logicalName) {
        return logicalToPhysicalColumnNames.containsKey(logicalName);
    }

    /**
     * Translate a physical column name into any columns in the physical table schema that map to it.
     *
     * @param name  The name of the physical column
     *
     * @return A stream of columns, empty if nothing in the schema matches this physical name
     */
    protected Stream<Column> getColumnForPhysicalName(String name) {
        if (physicalToLogicalColumnNames.containsKey(name)) {
            return physicalToLogicalColumnNames.get(name)
                    .stream()
                    .map(columnName -> getColumn(columnName, DimensionColumn.class).get());
        }
        Optional<? extends Column> metricColumn = getColumn(name, MetricColumn.class);
        return metricColumn.<Stream<Column>>map(Stream::of).orElseGet(Stream::empty);
    }

    /**
     * Granularity.
     *
     * @return the granularity for this schema
     */
    public ZonedTimeGrain getTimeGrain() {
        return timeGrain;
    }

    /**
     * Translate a map of physical availability data into logical available columns.
     *
     * @param availability  The raw availability for physical columns
     *
     * @return  The available data as mapped to columns in this schema
     */
    public Map<Column, List<Interval>> mapToColumns(Map<String, List<Interval>> availability) {
        return availability.entrySet().stream()
                .flatMap(entry -> getColumnForPhysicalName(entry.getKey()).map(column -> Pair.of(
                        column,
                        entry.getValue()
                )))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }
}
