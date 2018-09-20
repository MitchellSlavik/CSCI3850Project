import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebSearchProject - Phase 1
 * 
 * @author Mitchell Slavik
 * @author Colin Krist
 */
public class WebSearchProject {
  public static TreeSet<String> stopWords = new TreeSet<String>();
  public static ConcurrentHashMap<String, Object> tokens = new ConcurrentHashMap<String, Object>();
  public static ConcurrentHashMap<String, Integer> documentTokenTotals = new ConcurrentHashMap<>();
  
  public static Porter porter = new Porter();

  /**
   * Takes in a directory where the files to be processed are located. Uses threads to 
   * quickly process them and return an analysis of the files.
   * 
   * @param args
   */
  public static void main(String[] args) {
    // Make sure we have the right number of args
    if (args.length != 1) {
      System.out.println("Usage: java Main [path to docset folder]");
      return;
    }

    // Save start time
    long start = System.currentTimeMillis();

    // Read in stop word list (or set in our case)
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

    // Get all files
    File[] files = new File(args[0]).listFiles();
    int numFiles = files.length;
    
    // Start thread pool for processing files
    CountDownLatch latch = new CountDownLatch(numFiles);
    ExecutorService es = Executors.newFixedThreadPool(16);

    // Assign new task for each file
    for (File file : files) {
      es.execute(new FileReaderRunner(file, latch));
    }
    
    es.shutdown();

    // Show progress bar
    while (latch.getCount() != 0) {
      double count = (double) latch.getCount();
      showProgress((numFiles - count) / numFiles);
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    long afterReads = System.currentTimeMillis();
    long total = afterReads - start;
    System.out.println("Files processed! (Took " + total + "ms)             "); // space needed to remove progress bar
    System.out.println("");

    System.out.println("---------------------------");
    // printDatabase(tokens);
    System.out.println(documentTokenTotals);
    System.out.println(tokens.size());
    System.out.println("---------------------------");

    // Show total time taken to execute program
    total = System.currentTimeMillis() - start;
    System.out.println("Program complete! (Took " + total + "ms)");
    System.out.println("");
  }

  
  @SuppressWarnings("unchecked")
public static void printDatabase(ConcurrentHashMap<String, Object> tokens2) {
	  for( Entry<String, Object> s: tokens2.entrySet()) {
		  if( s.getValue() instanceof String) {
			  System.out.println(s.getKey() + " : " + s.getValue());
		  } else {
			  System.out.print(s.getKey() + " { ");
			  for ( Entry<String, Object> fileName: ((ConcurrentHashMap<String, Object>) s.getValue()).entrySet()) {
				  System.out.print(fileName.getKey());
				  System.out.print(fileName.getValue() +", ");
			  }
			  System.out.println(" } ");  
		  }
	  }
  }
  
  /**
   * Takes in the list of words and processes a top 10, bottom 10, and
   * 'meaningful' result from it and pretty prints the findings
   * 
   * @param words
   * @param listDesc
   */
  @SuppressWarnings("unchecked")
  public static void processResult(ConcurrentHashMap<String, Object> words, String listDesc) {
    ArrayList<String> top10 = new ArrayList<String>();
    ArrayList<String> bottom10 = new ArrayList<String>();

    TreeMap<Integer, ArrayList<String>> rankedWords = new TreeMap<>();

    System.out.println("Beginning analysis for " + listDesc + "...");
    for (Entry<String, Object> entry : words.entrySet()) {
      Integer n = 1;
      if(!(entry.getValue() instanceof String)) {
        n = ((CopyOnWriteArrayList<String>) entry.getValue()).size();
      }
      if (rankedWords.containsKey(n)) {
        rankedWords.get(n).add(entry.getKey());
      } else {
        ArrayList<String> a = new ArrayList<>();
        a.add(entry.getKey());
        rankedWords.put(n, a);
      }
    }
    // compile top 10 words
    Iterator<Integer> iterator = rankedWords.descendingKeySet().iterator();
    for (int i = 0; iterator.hasNext() && i < 10; i++) {
      if (top10.size() >= 10) {
        break;
      }
      ArrayList<String> topWords = rankedWords.get(iterator.next());
      for (String w : topWords) {
        if (top10.size() < 10) {
          top10.add(w);
        } else {
          break;
        }
      }
    }

    // compile bottom 10 words
    Iterator<Integer> iter = rankedWords.keySet().iterator();
    for (int i = 0; iter.hasNext() && i < 10; i++) {
      if (bottom10.size() >= 10) {
        break;
      }
      ArrayList<String> bottomWords = rankedWords.get(iter.next());
      for (String w : bottomWords) {
        if (bottom10.size() < 10) {
          bottom10.add(w);
        } else {
          break;
        }
      }
    }
    Collections.sort(top10);
    Collections.sort(bottom10);

    // compile meaningful words based off if their length > 5
    // since meaningful is relative at this point
    HashMap<String, Boolean> topMeaningful = new HashMap<>();
    for (String w : top10) {
      topMeaningful.put(w, w.length() > 5);
    }

    System.out.println("Unique keywords: " + words.size());
    System.out.println("Top 10 words: " + String.join(", ", top10));
    System.out.println("Top 10 meaningful words (meaningful = len>5): " + topMeaningful.toString());
    System.out.println("Bottom 10 words: " + String.join(", ", bottom10));
  }

  /**
   * Takes in a double (0-1) to indicate in the console the progress
   * of an operation
   * 
   * @param percent
   */
  public static void showProgress(double percent) {
    int len = 30;

    String progress = "";
    for (int i = 0; i < (int) (percent * len); i++) {
      progress += "#";
    }

    String space = "";
    for (int i = 0; i < len - (int) (percent * len); i++) {
      space += " ";
    }
    System.out.printf("|" + progress + space + "| %5.1f %%\r", percent * 100);
  }

  /**
   * Class that handles parsing the keywords in a single file
   */
  public static class FileReaderRunner implements Runnable {
    private File file;
    private String fileName;
    private CountDownLatch latch;

    /**
     * Construct a FileReaderRunner
     * 
     * @param File f - The file to be parsed
     * @param CountDownLatch latch - Notified when complete
     */
    public FileReaderRunner(File f, CountDownLatch latch) {
      this.file = f;
      this.latch = latch;
      this.fileName = file.getName();
    }

    /**
     * Read the entire file in as a string
     */
    private String readFile() {
      try {
        byte[] encoded = Files.readAllBytes(file.toPath());
        return new String(encoded, StandardCharsets.UTF_8);
      } catch (IOException e) {
        return "";
      }
    }

    /**
     * Called by the ExecutorService when a thread is ready, parses the
     * keywords from the file given in the constructor
     */
    @SuppressWarnings("unchecked")
    @Override
    public void run() {
      String content = readFile();
      content = content.replaceAll("</?.*?>", "").replaceAll("[`'\\.!?\\-,]", "").replaceAll("\\s+", " ").toLowerCase();
      String[] rawTokens = content.split(" ");
      Integer tokenCount = 0;

      for (String token : rawTokens) {
        if(token.equals("")) continue;
        
        boolean isStopWord = stopWords.contains(token);
        
        if (!isStopWord) {			// word is not a stop word, count it in total, stem, and insert into dataset
          tokenCount++;
        	
          String stemmedToken = porter.stripAffixes(token);
          if (!tokens.containsKey(stemmedToken)) {
            tokens.put(stemmedToken, fileName);			// conserve memory by setting fileName as string
          } else {
            Object o = tokens.get(stemmedToken);
            if(o instanceof String) {					// once we find the instance of this token again, convert to hashmap of documents
              ConcurrentHashMap<String, CopyOnWriteArrayList<Integer>> documentMap = new ConcurrentHashMap<>();
              CopyOnWriteArrayList<Integer> i = new CopyOnWriteArrayList<>();
              if(o == fileName) {						// if it's the same document again (pretty likely) set hit value to 2
            	 i.add(2);
            	 documentMap.put((String) o, i);
              } else {									// if it's not, add both documents to the hashmap
            	  i.add(1);	// add the previous doc string into the document list under the token
            	  documentMap.put((String) o, i);

            	  // add the new document into the doc list under the token
            	  CopyOnWriteArrayList<Integer> i2 = new CopyOnWriteArrayList<>();
            	  i2.add(1);
            	  documentMap.put(fileName, i2);
              }
              
              tokens.put(stemmedToken, documentMap); // overwrite previous docuemntMap with our new one
            } else {									// if we already have a hashmap of documents, find document ++ freq, else put new fileName in hashmap
              if(((ConcurrentHashMap<String, CopyOnWriteArrayList<Integer>>) o).containsKey(fileName)) {
            	  CopyOnWriteArrayList<Integer> totalList = ((ConcurrentHashMap<String, CopyOnWriteArrayList<Integer>>) o).get(fileName);
            	  Integer total = totalList.get(0);
            	  totalList.set(0, total+1);
            	  // ((ConcurrentHashMap<String, CopyOnWriteArrayList<Integer>>) o).remove(fileName);
            	  ((ConcurrentHashMap<String, CopyOnWriteArrayList<Integer>>) o).put(fileName, totalList);
            	  
            	  tokens.put(token, o); // write new data in
              } else {
            	  CopyOnWriteArrayList<Integer> total = new CopyOnWriteArrayList<>();
            	  total.add(1);
            	  ((ConcurrentHashMap<String, CopyOnWriteArrayList<Integer>>) o).put(fileName, total); // write value in for freq of word in document
            	  tokens.put(token, o); //write all docs back in for token
              }
            }
          }
        }
      }
      
      // once loop of tokens is done. add the final count to a 
      WebSearchProject.documentTokenTotals.put(fileName, tokenCount);
      
      // Tell the CountDownLatch we are done
      latch.countDown();
    }

  }
}