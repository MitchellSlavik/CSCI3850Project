import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  public static Semaphore printLock = new Semaphore(1);

  /**
   * Takes in a directory where the files to be processed are located. Uses
   * threads to quickly process them and return an analysis of the files.
   * 
   * @param args
   */
  public static void main(String[] args) {
    // Make sure we have the right number of args
    if (args.length != 2) {
      System.out.println("Usage: java Main [path to docset folder] [query file]");
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
    int numFiles = 0;
    
    if(files != null) {
      numFiles = files.length;
    }
    
    if(numFiles == 0) {
      System.out.println("Empty document set!");
      return;
    }

    // Start thread pool for processing files
    CountDownLatch latch = new CountDownLatch(numFiles);
    ExecutorService es = Executors.newFixedThreadPool(16);

    // Assign new task for each file
    for (File file : files) {
      es.execute(new FileReaderRunner(file, latch));
    }

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

    try {
      reader = new BufferedReader(new FileReader(new File(args[1])));
      String line;
      while ((line = reader.readLine()) != null) {
        if(!line.trim().equals("")) {
          es.execute(new QueryProcessingRunner(line));
        }
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    es.shutdown();
    try {
      es.awaitTermination(10, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    System.out.println("---------------------------");

    // Show total time taken to execute program
    total = System.currentTimeMillis() - start;
    System.out.println("Program complete! (Took " + total + "ms)");
    System.out.println("");
  }
  
  /**
   * Takes in a double (0-1) to indicate in the console the progress of an
   * operation
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
     * @param File
     *          f - The file to be parsed
     * @param CountDownLatch
     *          latch - Notified when complete
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
     * Called by the ExecutorService when a thread is ready, parses the keywords
     * from the file given in the constructor
     */
    @SuppressWarnings("unchecked")
    @Override
    public void run() {
      String content = readFile().replaceAll("\\s+", " ");

      // Attempt to find the title in the file, if not found or blank set to fileName
      Pattern pattern = Pattern.compile(".*?<TITLE>(.*?)</TITLE>.*");
      Matcher matcher = pattern.matcher(content);
      String title;
      if (matcher.matches()) {
        title = matcher.group(1).trim();

        if (title.equals("")) {
          title = fileName;
        }
      } else {
        title = fileName;
      }

      String[] rawTokens = content.replaceAll("</?.*?>", "").replaceAll("[`'\\.!?\\-,]", "").toLowerCase().split(" ");
      Integer tokenCount = 0;

      for (String token : rawTokens) {
        if (token.equals(""))
          continue;

        if (!stopWords.contains(token)) { // word is not a stop word, count it in total, stem, and insert into dataset
          tokenCount++;

          String stemmedToken = porter.stripAffixes(token);
          if (!tokens.containsKey(stemmedToken)) {
            tokens.put(stemmedToken, title); // conserve memory by setting fileName as string
          } else {
            Object o = tokens.get(stemmedToken);
            if (o instanceof String) { // once we find the instance of this token again, convert to hashmap of documents
              ConcurrentHashMap<String, Integer> documentMap = new ConcurrentHashMap<>();

              if (o.equals(title)) {
                documentMap.put((String) o, 2);
              } else {
                documentMap.put((String) o, 1);
                documentMap.put(title, 1);
              }

              tokens.put(stemmedToken, documentMap); // overwrite previous docuemntMap with our new one
            } else { // if we already have a hashmap of documents, find document ++ freq, else put new fileName in hashmap
              ConcurrentHashMap<String, Integer> map = (ConcurrentHashMap<String, Integer>) o;
              if (map.containsKey(title)) {
                map.put(title, map.get(title) + 1);
              } else {
                map.put(title, 1);
              }
            }
          }
        }
      }

      // once loop of tokens is done. add the final count to a 
      documentTokenTotals.put(title, tokenCount);

      // Tell the CountDownLatch we are done
      latch.countDown();
    }

  }

  /**
   * Class that handles processing a single query
   */
  public static class QueryProcessingRunner implements Runnable {
    private String query;

    /**
     * Construct a QueryProcessingRunner
     * 
     * @param String
     *          query - The query to be processed
     */
    public QueryProcessingRunner(String query) {
      this.query = query;
    }

    /**
     * Called by the ExecutorService when a thread is ready, parses the query and
     * compiles the results
     */
    @SuppressWarnings("unchecked")
    @Override
    public void run() {
      String[] queryTerms = query.replaceAll("[`'\\.!?\\-,]", "").toLowerCase().split(" ");
      ArrayList<String> stemmedTerms = new ArrayList<String>();

      Set<String> documents = new HashSet<String>();

      for (String term : queryTerms) {
        if (stopWords.contains(term.toLowerCase())) {
          continue;
        }

        String stemmedTerm = porter.stripAffixes(term.toLowerCase());

        stemmedTerms.add(stemmedTerm);

        Object o = tokens.get(stemmedTerm);
        if (o != null) {
          if (o instanceof String) {
            documents.add((String) o);
          } else {
            ConcurrentHashMap<String, Integer> docMap = (ConcurrentHashMap<String, Integer>) o;
            documents.addAll(docMap.keySet());
          }
        }
      }

      TreeSet<Ranking> rankings = new TreeSet<Ranking>();

      for (String doc : documents) {
        int matching = 0;
        for (String stemmedTerm : stemmedTerms) {
          Object o = tokens.get(stemmedTerm);

          if (o != null) {
            if (o instanceof String) {
              if (o.equals(doc)) {
                matching++;
              }
            } else {
              ConcurrentHashMap<String, Integer> docMap = (ConcurrentHashMap<String, Integer>) o;
              if (docMap.containsKey(doc)) {
                matching += docMap.get(doc);
              }
            }
          }
        }

        Integer total = documentTokenTotals.get(doc);
        if (total != null) {
          double rankingValue = matching / total.doubleValue();
          rankings.add(new Ranking(doc, rankingValue));
        }
      }

      try {
        printLock.acquire();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      System.out.println("---------------------------");
      System.out.println("Query: " + query);
      System.out.println("Top 10 Results:");

      int i = 1;
      Ranking curr = null;
      while (i < 11 && (curr = rankings.pollLast()) != null) {
        System.out.println(i + ": " + curr.getDoc());
        i++;
      }

      printLock.release();
    }
  }

  /**
   * Class that helps in sorting of rankings of documents
   */
  public static class Ranking implements Comparable<Ranking> {
    private String doc;
    private Double rankingValue;

    public Ranking(String doc, Double rankingValue) {
      this.doc = doc;
      this.rankingValue = rankingValue;
    }

    public String getDoc() {
      return doc;
    }

    @Override
    public int compareTo(Ranking o) {
      if (this.rankingValue < o.rankingValue) {
        return -1;
      } else if (this.rankingValue > o.rankingValue) {
        return 1;
      }
      return 0;
    }

  }
}