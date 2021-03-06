/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.sync.diffmatchpatch;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.aerogear.sync.diffmatchpatch.DiffMatchPatchEdit.Builder;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.jboss.aerogear.sync.diffmatchpatch.DiffMatchPatchDiff.Operation;

public final class JsonMapper {

    private static ObjectMapper om = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        om = new ObjectMapper();
        final SimpleModule module = new SimpleModule("DiffMatch", new Version(1, 0, 0, null, "aerogear", "sync"));
        module.addDeserializer(DiffMatchPatchEdit.class, new EditDeserializer());
        module.addSerializer(DiffMatchPatchEdit.class, new EditSerializer());
        module.addDeserializer(DiffMatchPatchMessage.class, new PatchMessageDeserializer());
        module.addSerializer(DiffMatchPatchMessage.class, new PatchMessageSerializer());
        om.registerModule(module);
        return om;
    }

    private JsonMapper() {
    }

    /**
     * Transforms from JSON to the type specified.
     *
     * @param json the json to be transformed.
     * @param type the Java type that the JSON should be transformed to.
     * @param <T> the type the class to convert to
     * @return T an instance of the type populated with data from the json message.
     */
    public static <T> T fromJson(final String json, final Class<T> type) {
        try {
            return om.readValue(json, type);
        } catch (final Exception e) {
            throw new RuntimeException("error trying to parse json [" + json + ']', e);
        }
    }

    /**
     * Transforms from Java object notation to JSON.
     *
     * @param obj the Java object to transform into JSON.
     * @return {@code String} the json representation for the object.
     */
    public static String toJson(final Object obj) {
        try {
            return om.writeValueAsString(obj);
        } catch (final Exception e) {
            throw new RuntimeException("error trying to parse json [" + obj + ']', e);
        }
    }

    public static String toString(final JsonNode jsonNode) {
        try {
            return om.writeValueAsString(jsonNode);
        } catch (final Exception e) {
            throw new RuntimeException("error trying to serialize jsonNode [" + jsonNode + ']', e);
        }
    }

    /**
     * Return a {@link JsonNode} for the passed in JSON string.
     *
     * @param json the string to be parsed.
     * @return JsonNode the JsonNode representing the passed-in JSON string.
     */
    public static JsonNode asJsonNode(final String json) {
        try {
            return om.readTree(json);
        } catch (final IOException e) {
            throw new RuntimeException("error trying to parse json [" + json + ']', e);
        }
    }

    public static ObjectNode newObjectNode() {
        return om.createObjectNode();
    }

    public static ArrayNode newArrayNode() {
        return om.createArrayNode();
    }

    private static class PatchMessageDeserializer extends JsonDeserializer<DiffMatchPatchMessage> {

        @Override
        public DiffMatchPatchMessage deserialize(final JsonParser jp, final DeserializationContext ctxt) throws IOException {
            final ObjectCodec oc = jp.getCodec();
            final JsonNode node = oc.readTree(jp);
            final String documentId = node.get("id").asText();
            final String clientId = node.get("clientId").asText();
            final JsonNode jsonEdits = node.get("edits");
            final Queue<DiffMatchPatchEdit> edits = new ConcurrentLinkedQueue<DiffMatchPatchEdit>();
            if (jsonEdits.isArray()) {
                for (JsonNode edit : jsonEdits) {
                    if (edit.isNull()) {
                        continue;
                    }
                    final Builder eb = DiffMatchPatchEdit.withChecksum(edit.get("checksum").asText());
                    eb.clientVersion(edit.get("clientVersion").asLong());
                    eb.serverVersion(edit.get("serverVersion").asLong());
                    final JsonNode diffsNode = edit.get("diffs");
                    if (diffsNode.isArray()) {
                        for (JsonNode d : diffsNode) {
                            if (d.isNull()) {
                                continue;
                            }
                            eb.diff(new DiffMatchPatchDiff(Operation.valueOf(d.get("operation").asText()), d.get("text").asText()));
                        }
                    }
                    edits.add(eb.build());
                }
            }
            return new DiffMatchPatchMessage(documentId, clientId, edits);
        }
    }

    private static class PatchMessageSerializer extends JsonSerializer<DiffMatchPatchMessage> {

        @Override
        public void serialize(final DiffMatchPatchMessage patchMessage,
                              final JsonGenerator jgen,
                              final SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            jgen.writeStringField("msgType", "patch");
            jgen.writeStringField("id", patchMessage.documentId());
            jgen.writeStringField("clientId", patchMessage.clientId());
            jgen.writeArrayFieldStart("edits");
            for (DiffMatchPatchEdit edit : patchMessage.edits()) {
                if (edit == null) {
                    continue;
                }
                jgen.writeStartObject();
                jgen.writeNumberField("clientVersion", edit.clientVersion());
                jgen.writeNumberField("serverVersion", edit.serverVersion());
                jgen.writeStringField("checksum", edit.checksum());
                jgen.writeArrayFieldStart("diffs");
                if (edit.diff() != null) {
                    for (DiffMatchPatchDiff diff : edit.diff().diffs()) {
                        jgen.writeStartObject();
                        jgen.writeStringField("operation", diff.operation().toString());
                        jgen.writeStringField("text", diff.text());
                        jgen.writeEndObject();
                    }
                    jgen.writeEndArray();
                }
                jgen.writeEndObject();
            }
            jgen.writeEndArray();
            jgen.writeEndObject();
        }
    }

    private static class EditDeserializer extends JsonDeserializer<DiffMatchPatchEdit> {

        @Override
        public DiffMatchPatchEdit deserialize(final JsonParser jp, final DeserializationContext ctxt) throws IOException {
            final ObjectCodec oc = jp.getCodec();
            final JsonNode edit = oc.readTree(jp);
            final Builder eb = DiffMatchPatchEdit.withChecksum(edit.get("checksum").asText());
            eb.clientVersion(edit.get("clientVersion").asLong());
            eb.serverVersion(edit.get("serverVersion").asLong());
            final JsonNode diffsNode = edit.get("diffs");
            if (diffsNode.isArray()) {
                for (JsonNode d : diffsNode) {
                    eb.diff(new DiffMatchPatchDiff(Operation.valueOf(d.get("operation").asText()), d.get("text").asText()));
                }
            }
            return eb.build();
        }
    }

    private static class EditSerializer extends JsonSerializer<DiffMatchPatchEdit> {

        @Override
        public void serialize(final DiffMatchPatchEdit edit,
                              final JsonGenerator jgen,
                              final SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            jgen.writeStringField("msgType", "patch");
            jgen.writeNumberField("clientVersion", edit.clientVersion());
            jgen.writeNumberField("serverVersion", edit.serverVersion());
            jgen.writeStringField("checksum", edit.checksum());
            jgen.writeArrayFieldStart("diffs");
            if (edit.diff() != null) {
                for (DiffMatchPatchDiff diff : edit.diff().diffs()) {
                    jgen.writeStartObject();
                    jgen.writeStringField("operation", diff.operation().toString());
                    jgen.writeStringField("text", diff.text());
                    jgen.writeEndObject();
                }
            }
            jgen.writeEndArray();
        }
    }
}

