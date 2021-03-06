/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.facet.range;

import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.InternalFacet;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * @author kimchy (shay.banon)
 */
public class InternalRangeFacet implements RangeFacet, InternalFacet {

    private static final String STREAM_TYPE = "range";

    public static void registerStreams() {
        Streams.registerStream(STREAM, STREAM_TYPE);
    }

    static Stream STREAM = new Stream() {
        @Override public Facet readFacet(String type, StreamInput in) throws IOException {
            return readRangeFacet(in);
        }
    };

    @Override public String streamType() {
        return STREAM_TYPE;
    }

    private String name;

    private String keyFieldName;

    private String valueFieldName;

    Entry[] entries;

    InternalRangeFacet() {
    }

    public InternalRangeFacet(String name, String keyFieldName, String valueFieldName, Entry[] entries) {
        this.name = name;
        this.keyFieldName = keyFieldName;
        this.valueFieldName = valueFieldName;
        this.entries = entries;
    }

    @Override public String name() {
        return this.name;
    }

    @Override public String getName() {
        return name();
    }

    @Override public String type() {
        return RangeFacet.TYPE;
    }

    @Override public String getType() {
        return RangeFacet.TYPE;
    }

    @Override public String keyFieldName() {
        return this.keyFieldName;
    }

    @Override public String getKeyFieldName() {
        return keyFieldName();
    }

    @Override public String valueFieldName() {
        return this.valueFieldName;
    }

    @Override public String getValueFieldName() {
        return valueFieldName();
    }

    @Override public List<Entry> entries() {
        return ImmutableList.copyOf(entries);
    }

    @Override public List<Entry> getEntries() {
        return entries();
    }

    @Override public Iterator<Entry> iterator() {
        return entries().iterator();
    }

    public static InternalRangeFacet readRangeFacet(StreamInput in) throws IOException {
        InternalRangeFacet facet = new InternalRangeFacet();
        facet.readFrom(in);
        return facet;
    }

    @Override public void readFrom(StreamInput in) throws IOException {
        name = in.readUTF();
        keyFieldName = in.readUTF();
        valueFieldName = in.readUTF();
        entries = new Entry[in.readVInt()];
        for (int i = 0; i < entries.length; i++) {
            Entry entry = new Entry();
            entry.from = in.readDouble();
            entry.to = in.readDouble();
            if (in.readBoolean()) {
                entry.fromAsString = in.readUTF();
            }
            if (in.readBoolean()) {
                entry.toAsString = in.readUTF();
            }
            entry.count = in.readVLong();
            entry.total = in.readDouble();
            entries[i] = entry;
        }
    }

    @Override public void writeTo(StreamOutput out) throws IOException {
        out.writeUTF(name);
        out.writeUTF(keyFieldName);
        out.writeUTF(valueFieldName);
        out.writeVInt(entries.length);
        for (Entry entry : entries) {
            out.writeDouble(entry.from);
            out.writeDouble(entry.to);
            if (entry.fromAsString == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                out.writeUTF(entry.fromAsString);
            }
            if (entry.toAsString == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                out.writeUTF(entry.toAsString);
            }
            out.writeVLong(entry.count);
            out.writeDouble(entry.total);
        }
    }

    static final class Fields {
        static final XContentBuilderString _TYPE = new XContentBuilderString("_type");
        static final XContentBuilderString _KEY_FIELD = new XContentBuilderString("_key_field");
        static final XContentBuilderString _VALUE_FIELD = new XContentBuilderString("_value_field");
        static final XContentBuilderString RANGES = new XContentBuilderString("ranges");
        static final XContentBuilderString FROM = new XContentBuilderString("from");
        static final XContentBuilderString FROM_STR = new XContentBuilderString("from_str");
        static final XContentBuilderString TO = new XContentBuilderString("to");
        static final XContentBuilderString TO_STR = new XContentBuilderString("to_str");
        static final XContentBuilderString COUNT = new XContentBuilderString("count");
        static final XContentBuilderString TOTAL = new XContentBuilderString("total");
        static final XContentBuilderString MEAN = new XContentBuilderString("mean");
    }

    @Override public void toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(name);
        builder.field(Fields._TYPE, "range");
        builder.field(Fields._KEY_FIELD, keyFieldName);
        builder.field(Fields._VALUE_FIELD, valueFieldName);
        builder.startArray(Fields.RANGES);
        for (Entry entry : entries) {
            builder.startObject();
            if (!Double.isInfinite(entry.from)) {
                builder.field(Fields.FROM, entry.from);
            }
            if (entry.fromAsString != null) {
                builder.field(Fields.FROM_STR, entry.fromAsString);
            }
            if (!Double.isInfinite(entry.to)) {
                builder.field(Fields.TO, entry.to);
            }
            if (entry.toAsString != null) {
                builder.field(Fields.TO_STR, entry.toAsString);
            }
            builder.field(Fields.COUNT, entry.count());
            builder.field(Fields.TOTAL, entry.total());
            builder.field(Fields.MEAN, entry.mean());
            builder.endObject();
        }
        builder.endArray();
        builder.endObject();
    }
}
