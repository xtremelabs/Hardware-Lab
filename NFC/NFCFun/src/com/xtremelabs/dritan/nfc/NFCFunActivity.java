package com.xtremelabs.dritan.nfc;

import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Vibrator;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * Demo Activity showing NFC capabilities of reading and writing.
 * @author Dritan Xhabija
 *
 */
public class NFCFunActivity extends Activity {

	Button readMode, writeMode;
	private final String READ_MODE = "READ_MODE";

	TextView status;

	int cardType = 0;
	private final int CARD = 1;
	private final int CIRCLE = 2;

	private boolean read_mode = true; //default mode is read mode
	private AsyncTask async;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		//button initialization
		readMode = (Button) findViewById(R.id.readMode);
		readMode.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				log("Set to Read Mode");
				saveBoolean(READ_MODE, true);
			}
		});
		readMode.setOnLongClickListener(new OnLongClickListener() {
			
			@Override
			public boolean onLongClick(View v) {
				final Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
				if (async!=null){
					async.cancel(true);
				}
				
				vib.cancel();
				return false;
			}
		});

		writeMode = (Button) findViewById(R.id.writeMode);
		writeMode.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				log("Set to Write Mode");
				saveBoolean(READ_MODE, false);
			}
		});

		//the "logger window"
		status = (TextView) findViewById(R.id.status);

		//fetch the mode to go into
		read_mode = getBoolean(READ_MODE, true);
	}


	/**
	 * Let's handle the  
	 */
	@Override
	protected void onResume() {
		super.onResume();
		status.setText("");

		Intent intent = getIntent();
		log("Got intent action: "+intent.getAction());

		android.nfc.Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		log("GOT TAG: "+tag);


		if (tag!=null){
			log("\nAvailable Technologies:");
			String [] techs = tag.getTechList();
			for (String tech : techs){
				log(tech);
			}
		}

		//write to NFC card if app is in write-mode
		if (!writeMode(tag)){ //else go to read mode

			//intent action defines the kind NFC tech to use.
			String imode = intent.getAction();

			boolean isTag = imode.equals(NfcAdapter.ACTION_TAG_DISCOVERED);
			boolean isTech = imode.equals(NfcAdapter.ACTION_TECH_DISCOVERED);
			boolean isNdef = imode.equals(NfcAdapter.ACTION_NDEF_DISCOVERED);

			log("READ MODE: "+(isNdef? " NDEF " : isTech ? " Predefined Tech " : isTag ? " Only basic tag can be recognized" : "No NFC card present"));

			//start reading
			readMode();
		}

		executeFunk();

	}


	/**
	 * Read out everything on the swiped card.
	 */
	private void readMode() {
		Intent intent = getIntent();
		NdefMessage[] msgs = null;
		if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
			Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			if (rawMsgs != null) {
				msgs = new NdefMessage[rawMsgs.length];
				String out = "NDEF MESSAGE: ";
				for (int i = 0; i < rawMsgs.length; i++) {
					msgs[i] = (NdefMessage) rawMsgs[i];
					out += msgs[i];
				}
				log(out);
			} else {
				log("NDEF Messages IS NULL");


				byte []id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);

				for (byte b : id){
					log("GOT EXTRA ID: "+b);
				}

				if (id[0]==-70){
					cardType = CARD;
				} else if (id[0]==-98){
					cardType = CIRCLE;
				}

			}
		}
		if (msgs!=null){
			for (NdefMessage msg : msgs){
				byte [] mmsg = msg.toByteArray();
				String out = "Got message: ";

				for (byte bb : mmsg){
					out += bb+" ";
				}
				log(out);
			}
		}
	}


	/**
	 * If app is set to be in write mode, it will try to
	 * write to the NFC card
	 * @param tag Tag object identifying the scanned card
	 * @return whether the app is in write mode (and thus a write was attempted)
	 */
	private boolean writeMode(Tag tag){
		boolean ret = false;
		if (ret = (!read_mode && tag!=null)){
			log("WRITE MODE");
			writeTag(tag, "HELLO XTREMERS");
		}
		return ret;
	}

	public void writeTag(Tag tag, String tagText) {
		log("Attempting to write: "+tagText);

		byte [] bytes = tagText.getBytes();

		MifareClassic mc = MifareClassic.get(tag);

		//pad the remaining space with 0's
		byte [] toWrite = new byte[MifareClassic.BLOCK_SIZE];
		for (int i=0; i<MifareClassic.BLOCK_SIZE; i++) {
			if (i < bytes.length) toWrite[i] = bytes[i];
			else toWrite[i] = 0;
		}
		log("Got MiFare blocks: "+mc.getBlockCount()+", MAX TRANSCEIVE LENGTH: "+mc.getMaxTransceiveLength()+", bytes length: "+toWrite.length);

		String out = "";
		for (byte b:toWrite){
			out+=b+" ";
		}

		log("Trying to write block 1: "+out);

		try {
			mc.connect();
			mc.authenticateSectorWithKeyB(1, MifareClassic.KEY_DEFAULT);
			mc.writeBlock(1, toWrite);
			//			nc.transceive(toWrite);


		} catch (IOException e) {
			log("IOException while closing MifareClassic..."+e);

		} finally {
			try {
				mc.close();
			} catch (IOException e) {
				log("IOException while closing Mifare Classic..."+ e);
				e.printStackTrace();
			}
		}
	}


	private void executeFunk() {
		final Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		log("CARD TYPE: "+cardType);


		async = new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				int runs = 0;
				if (cardType==CIRCLE){
					while(runs++<10){
						v.vibrate(400);
						try {
							Thread.sleep(600);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				} else if (cardType==CARD){
					while(runs++<1){
						
						try {
							v.vibrate(50);
							Thread.sleep(50);
							v.vibrate(50);
							Thread.sleep(50);
							v.vibrate(50);
							Thread.sleep(50);
							v.vibrate(50);
							Thread.sleep(100);
							v.vibrate(100);
							Thread.sleep(100);
							v.vibrate(100);
							Thread.sleep(100);
							v.vibrate(100);
							Thread.sleep(100);
							v.vibrate(100);
							Thread.sleep(200);
							v.vibrate(200);
							Thread.sleep(200);
							v.vibrate(200);
							Thread.sleep(200);
							v.vibrate(200);
							Thread.sleep(200);
							v.vibrate(200);
							Thread.sleep(300);
							v.vibrate(300);
							Thread.sleep(400);
							v.vibrate(400);
							Thread.sleep(400);
							v.vibrate(400);
							Thread.sleep(400);
							v.vibrate(400);
							Thread.sleep(400);
							v.vibrate(400);
							Thread.sleep(400);
							v.vibrate(400);
							Thread.sleep(500);
							v.vibrate(500);
							Thread.sleep(500);
							v.vibrate(500);
							Thread.sleep(500);
							v.vibrate(500);
							Thread.sleep(500);
							v.vibrate(500);
							Thread.sleep(600);
							v.vibrate(600);
							Thread.sleep(600);
							v.vibrate(600);
							Thread.sleep(600);
							v.vibrate(600);
							Thread.sleep(600);
							v.vibrate(600);
							Thread.sleep(700);
							v.vibrate(700);
							Thread.sleep(700);
							v.vibrate(700);
							Thread.sleep(700);
							v.vibrate(700);
							Thread.sleep(700);
							v.vibrate(700);
							Thread.sleep(700);
							v.vibrate(700);
							Thread.sleep(700);
							v.vibrate(700);
							Thread.sleep(800);
							v.vibrate(800);
							Thread.sleep(800);
							v.vibrate(800);
							Thread.sleep(800);
							v.vibrate(800);
							Thread.sleep(800);
							v.vibrate(800);
							Thread.sleep(800);
							v.vibrate(800);
							Thread.sleep(900);
							v.vibrate(900);
							Thread.sleep(900);
							v.vibrate(900);
							Thread.sleep(900);
							v.vibrate(900);
							Thread.sleep(900);
							v.vibrate(900);
							Thread.sleep(1000);
							v.vibrate(1000);
							Thread.sleep(1000);
							v.vibrate(1000);
							Thread.sleep(1000);
							v.vibrate(1000);
							Thread.sleep(1000);
							v.vibrate(1000);
							
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				cardType = 0;
				return null;
			}
		}.execute();



	}

	public void log(String msg){
		status.append("\n"+msg);
	}


	/** 
	 * Use SharedPreferences for storing the mode of the app.
	 */

	private SharedPreferences getShPreferences() {
		return getSharedPreferences("NFC FUN", 0); 
	}

	private SharedPreferences.Editor getSharedEditor(){
		return getShPreferences().edit();
	}


	public void saveBoolean(String key, boolean value){
		SharedPreferences.Editor editor = getSharedEditor();
		editor.putBoolean(key, value);
		editor.commit();
	}

	public boolean getBoolean(String key, boolean defaultValue){
		return getShPreferences().getBoolean(key, defaultValue);
	}

}