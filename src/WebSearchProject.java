import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main
 */
public class WebSearchProject {
  public static ArrayList<String> stopWords = new ArrayList<String>();
  public static ConcurrentHashMap<String, CopyOnWriteArrayList<String>> keywords = new ConcurrentHashMap<String, CopyOnWriteArrayList<String>>();
  
  public static Porter porter = new Porter();
  
  public static void main(String[] args) {
    if(args.length != 1) {
      System.out.println("Usage: java Main [path to docset folder]");
      return;
    }
    
    long start = System.currentTimeMillis();
    
    BufferedReader reader;
    try {
      reader = new BufferedReader(new FileReader("stop-word-list.txt"));
      String line = reader.readLine();
      while (line != null) {
        stopWords.add(line.toLowerCase());
        line = reader.readLine();
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    File f = new File(args[0]);
    
    File[] files = f.listFiles();
    int numFiles = files.length;
    CountDownLatch latch = new CountDownLatch(numFiles);
    ExecutorService es = Executors.newFixedThreadPool(1);
    
    for(File file : files) {
      es.execute(new FileReaderRunner(file, latch));
    }
    
    while(latch.getCount() != 0) {
      double count = (double) latch.getCount();
      showProgress((numFiles - count) / numFiles);
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    
    long total = System.currentTimeMillis() - start;
    System.out.println("Files processed! (Took "+total+"ms)             "); // space needed to remove progress bar
    System.out.println("Beginning analysis...");
    System.out.println(keywords.size() + " unique keywords");
  }
  
  public static void showProgress(double percent) {
    int len = 30;
    
    String progress = "";
    for(int i = 0; i < (int)(percent * len); i++) {
      progress += "#";
    }
    
    String space = "";
    for(int i = 0; i < len - (int)(percent * len); i++) {
      space += " ";
    }
    System.out.printf("|"+progress+space+"| %5.1f %%\r", percent * 100);
  }
  
  public static class FileReaderRunner implements Runnable {
    
    private File file;
    private String fileName;
    private CountDownLatch latch;
    
    public FileReaderRunner(File f, CountDownLatch latch) {
      this.file = f;
      this.latch = latch;
      this.fileName = file.getName();
    }
    
    private String readFile() {
      try {
        byte[] encoded = Files.readAllBytes(file.toPath());
        return new String(encoded, StandardCharsets.UTF_8);
      } catch (IOException e) {
        return "";
      }
    }

    @Override
    public void run() {
      String content = readFile();
      content = content.replaceAll("</?.*?>", "").replaceAll("[`'\\.!?\\-,]", "").replaceAll("\\s+", " ").toLowerCase();
      String[] tokens = content.split(" ");
      for(String token : tokens) {
        if(!keywords.containsKey(token)) {
          CopyOnWriteArrayList<String> arr = new CopyOnWriteArrayList<String>();
          arr.add(fileName);
          keywords.put(token, arr);
        } else {
          keywords.get(token).add(fileName);
        }
      }
      latch.countDown();
    }
    
  }
}