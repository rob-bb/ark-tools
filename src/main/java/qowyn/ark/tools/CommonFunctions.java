package qowyn.ark.tools;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;
import javax.json.stream.JsonParser;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import qowyn.ark.ArkSavegame;
import qowyn.ark.GameObject;
import qowyn.ark.types.ObjectReference;

public class CommonFunctions {

  public static boolean onlyTamed(GameObject animal) {
    return animal.getProperty("TamedAtTime") != null;
  }

  public static boolean onlyWild(GameObject animal) {
    return animal.getProperty("TamedAtTime") == null;
  }

  public static int getBaseLevel(GameObject animal, ArkSavegame saveFile) {
    ObjectReference statusComponentReference = animal.getPropertyValue("MyCharacterStatusComponent", ObjectReference.class);

    if (statusComponentReference == null) {
      return 0;
    }

    GameObject statusComponent = statusComponentReference.getObject(saveFile);

    if (statusComponent == null) {
      return 0;
    }

    Integer baseLevel = statusComponent.getPropertyValue("BaseCharacterLevel", Integer.class);

    return baseLevel != null ? baseLevel : 0;
  }

  public static int getFullLevel(GameObject animal, ArkSavegame saveFile) {
    ObjectReference statusComponentReference = animal.getPropertyValue("MyCharacterStatusComponent", ObjectReference.class);

    if (statusComponentReference == null) {
      return 0;
    }

    GameObject statusComponent = statusComponentReference.getObject(saveFile);

    if (statusComponent == null) {
      return 0;
    }

    Integer baseLevel = statusComponent.getPropertyValue("BaseCharacterLevel", Integer.class);
    Short extraLevel = statusComponent.getPropertyValue("ExtraCharacterLevel", Short.class);
    
    int level = 0;
    
    if (baseLevel != null) {
      level += baseLevel;
    }
    if (extraLevel != null) {
      level += extraLevel;
    }

    return level;
  }

  public static void writeJson(OutputStream out, JsonObject o, OptionHandler oh) throws IOException {
    if (oh.usePretty()) {
      Map<String, Object> properties = new HashMap<>(1);
      properties.put(JsonGenerator.PRETTY_PRINTING, true);

      JsonWriterFactory jwf = Json.createWriterFactory(properties);
      try (JsonWriter writer = jwf.createWriter(out)) {
        writer.writeObject(o);
      }
    } else {
      try (JsonWriter writer = Json.createWriter(out)) {
        writer.writeObject(o);
      }
    }
  }

  public static void writeJson(String outFile, JsonObject o, OptionHandler oh) throws IOException {
    try (FileOutputStream out = new FileOutputStream(outFile)) {
      writeJson(out, o, oh);
    }
  }

  public static void writeJson(OutputStream out, Consumer<JsonGenerator> writeJson, OptionHandler oh) throws IOException {
    if (oh.usePretty()) {
      Map<String, Object> properties = new HashMap<>(1);
      properties.put(JsonGenerator.PRETTY_PRINTING, true);

      JsonGeneratorFactory jgf = Json.createGeneratorFactory(properties);
      try (JsonGenerator jg = jgf.createGenerator(out)) {
        writeJson.accept(jg);
      }
    } else {
      try (JsonGenerator jg = Json.createGenerator(out)) {
        writeJson.accept(jg);
      }
    }
  }

  public static void writeJson(String outFile, Consumer<JsonGenerator> writeJson, OptionHandler oh) throws IOException {
    try (FileOutputStream out = new FileOutputStream(outFile)) {
      writeJson(out, writeJson, oh);
    }
  }

  public static JsonObject readJson(String inFile) throws IOException {
    try (FileReader fileReader = new FileReader(inFile)) {
      JsonReader reader = Json.createReader(fileReader);
      return reader.readObject();
    }
  }

  public static void readJson(String inFile, Consumer<JsonParser> parseJson) throws IOException {
    try (FileReader fileReader = new FileReader(inFile)) {
      JsonParser parser = Json.createParser(fileReader);
      parseJson.accept(parser);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> void processAsyncronously(EventFactory<T> factory, EventHandler<T> consumer, Consumer<RingBuffer<T>> producer, int bufferSize) {

    Disruptor<T> disruptor = new Disruptor<>(factory, bufferSize, Executors.defaultThreadFactory(), ProducerType.SINGLE, new YieldingWaitStrategy());

    disruptor.handleEventsWith(consumer);

    disruptor.start();

    RingBuffer<T> ringBuffer = disruptor.getRingBuffer();

    producer.accept(ringBuffer);

    disruptor.shutdown();
  }

}
