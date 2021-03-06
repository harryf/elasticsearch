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

package org.elasticsearch.index.field.data.support;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.util.StringHelper;
import org.elasticsearch.index.field.data.FieldData;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author kimchy (shay.banon)
 */
public class FieldDataLoader {

    @SuppressWarnings({"StringEquality"})
    public static <T extends FieldData> T load(IndexReader reader, String field, TypeLoader<T> loader) throws IOException {

        loader.init();

        field = StringHelper.intern(field);
        int[] ordinals = new int[reader.maxDoc()];
        int[][] multiValueOrdinals = null;

        int t = 1;  // current term number

        TermDocs termDocs = reader.termDocs();
        TermEnum termEnum = reader.terms(new Term(field));
        try {
            do {
                Term term = termEnum.term();
                if (term == null || term.field() != field) break;
                loader.collectTerm(term.text());
                termDocs.seek(termEnum);
                while (termDocs.next()) {
                    int doc = termDocs.doc();
                    if (multiValueOrdinals != null) {
                        int[] ordinalPerDoc = multiValueOrdinals[doc];
                        if (ordinalPerDoc == null) {
                            ordinalPerDoc = new int[1];
                            ordinalPerDoc[0] = t;
                            multiValueOrdinals[doc] = ordinalPerDoc;
                        } else {
                            ordinalPerDoc = Arrays.copyOf(ordinalPerDoc, ordinalPerDoc.length + 1);
                            ordinalPerDoc[ordinalPerDoc.length - 1] = t;
                            multiValueOrdinals[doc] = ordinalPerDoc;
                        }
                    } else {
                        int ordinal = ordinals[doc];
                        if (ordinal == 0) { // still not multi valued...
                            ordinals[doc] = t;
                        } else {
                            // move to multi valued
                            multiValueOrdinals = new int[reader.maxDoc()][];
                            for (int i = 0; i < ordinals.length; i++) {
                                ordinal = ordinals[i];
                                if (ordinal != 0) {
                                    multiValueOrdinals[i] = new int[1];
                                    multiValueOrdinals[i][0] = ordinal;
                                }
                            }
                            // now put the current "t" value
                            int[] ordinalPerDoc = multiValueOrdinals[doc];
                            if (ordinalPerDoc == null) {
                                ordinalPerDoc = new int[1];
                                ordinalPerDoc[0] = t;
                                multiValueOrdinals[doc] = ordinalPerDoc;
                            } else {
                                ordinalPerDoc = Arrays.copyOf(ordinalPerDoc, ordinalPerDoc.length + 1);
                                ordinalPerDoc[ordinalPerDoc.length - 1] = t;
                                multiValueOrdinals[doc] = ordinalPerDoc;
                            }
                        }
                    }
                }

                t++;
            } while (termEnum.next());
        } catch (RuntimeException e) {
            if (e.getClass().getName().endsWith("StopFillCacheException")) {
                // all is well, in case numeric parsers are used.
            } else {
                throw e;
            }
        } finally {
            termDocs.close();
            termEnum.close();
        }

        if (multiValueOrdinals != null) {
            return loader.buildMultiValue(field, multiValueOrdinals);
        } else {
            return loader.buildSingleValue(field, ordinals);
        }
    }

    public static interface TypeLoader<T extends FieldData> {

        void init();

        void collectTerm(String term);

        T buildSingleValue(String fieldName, int[] ordinals);

        T buildMultiValue(String fieldName, int[][] ordinals);
    }

    public static abstract class FreqsTypeLoader<T extends FieldData> implements TypeLoader<T> {

        protected FreqsTypeLoader() {
        }

        @Override public void init() {
        }
    }
}
