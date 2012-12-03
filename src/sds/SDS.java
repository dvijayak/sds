/*
 * Copyright 1999-2004 Carnegie Mellon University.
 * Portions Copyright 2004 Sun Microsystems, Inc.
 * Portions Copyright 2004 Mitsubishi Electric Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 */

package sds;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.speech.recognition.GrammarException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.cmu.sphinx.frontend.util.Microphone;
import edu.cmu.sphinx.jsgf.JSGFGrammar;
import edu.cmu.sphinx.jsgf.JSGFGrammarException;
import edu.cmu.sphinx.jsgf.JSGFGrammarParseException;
import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.util.props.ConfigurationManager;


/**
 * A Spoken Dialogue System built using Sphinx-4. This application uses the Sphinx-4
 * endpointer, which automatically segments incoming audio into utterances and silences.
 */
public class SDS {

	private Recognizer recognizer;
	private JSGFGrammar grammarManager;		
	
	private Map<String, Question[]> categories;	
	
    public static void main(String[] args) throws IOException, JSGFGrammarParseException, JSGFGrammarException, GrammarException {
        ConfigurationManager cm;

        if (args.length > 0) {
            cm = new ConfigurationManager(args[0]);
        } else {
            cm = new ConfigurationManager(SDS.class.getResource("sds.config.xml"));
        }
                
        SDS sds = new SDS();
        // Create and allocate the recognizer
        sds.recognizer = (Recognizer) cm.lookup("recognizer");
        sds.recognizer.allocate();
        
        // Get the JSGFGrammar component
    	sds.grammarManager = (JSGFGrammar) cm.lookup("jsgfGrammar");    	    	              

        // start the microphone or exit the program if this is not possible
        Microphone microphone = (Microphone) cm.lookup("microphone");
        if (!microphone.startRecording()) {
            System.out.println("Cannot start microphone.");
            sds.recognizer.deallocate();
            System.exit(1);
        }

        System.out.println("Welcome to Sphinx4 Jeopardy!");
        System.out.println("============================\n");
        sds.drawGameBoard();        

        int score = 0;
        List<String> categories = new ArrayList<String>();
        List<String> points = new ArrayList<String>();
        // loop the recognition until the user says "quit"
        while (true) {
        	// Update the grammar
//        	System.out.println("Updating the grammar...");
        	sds.loadAndRecognize("gameboard");            
       
            // Start speaking and recognize input speech
        	System.out.println("Choose a command: [new game | quit] [<category> <points>]");
            Result result = sds.recognizer.recognize();

            if (result != null) {            	
                String resultText = result.getBestFinalResultNoFiller();                
                
                // If user says Quit, exit game
                if (resultText.equalsIgnoreCase("quit")) {
                	System.out.println("Bye now!");                	
                	sds.recognizer.deallocate();
                	System.exit(1);
                }
                
//                if (resultText.equalsIgnoreCase())
                
                System.out.println("You said: " + resultText + '\n');
            } else {
                System.out.println("I can't hear what you said.\n");
            }
           
        }
    }   
    
    /**
     * Load the grammar with the given grammar name. A previously loaded
     * grammar can be updated in this fashion.
     * 
     * @throws IOException if an I/O error occurs
     * @throws GrammarException if a grammar format error is detected
     */
    private void loadAndRecognize(String grammarName) throws GrammarException {
        try {
            grammarManager.loadJSGF(grammarName);
        } catch (JSGFGrammarException e) {
            throw new GrammarException (e.getMessage());
        } catch (JSGFGrammarParseException e) {
            throw new GrammarException (e.getMessage());
        } catch (IOException e) {
        	e.printStackTrace();
        }
        
//        dumpSampleSentences(grammarName);
    }



    /**
     * Dumps out a set of sample sentences for this grammar.  
     *  TODO: Note the current
     *  implementation just generates a large set of random utterances
     *  and tosses away any duplicates. There's no guarantee that this
     *  will generate all of the possible utterances. (yep, this is a hack)
     *
     */
    private void dumpSampleSentences(String title) {
        System.out.println(" ====== " + title + " ======");
        System.out.println("Possible commands: \n");
        grammarManager.dumpRandomSentences(200);
        System.out.println(" ============================");
//        System.out.println("Start speaking. Say Quit to quit.\n");
    }
    
    private void drawGameBoard () {
    	System.out.println("Gameboard");
        System.out.println("=========");
        System.out.println("Categories: Course, ASR");
        System.out.println("Points:     10, 20, 35, 60");
        System.out.println("=========\n");
    }
    
    // Initialize gameboard values
    private void loadGameBoard () {
    	try {    		    	
			BufferedReader br = new BufferedReader(new FileReader(new File("src/sds/game.json")));
			
			StringBuilder output = new StringBuilder();
			String line = null;			
			while ((line = br.readLine()) != null) {
				output.append(line);
			}
			br.close();
			String gameProperties = output.toString();
			
			try {								
				JSONObject bank = new JSONObject(gameProperties);				
				JSONArray categories = bank.getJSONArray("categories");
				
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    class Question {
    	String question;
    	String answer;
    	int points;
    }
}
