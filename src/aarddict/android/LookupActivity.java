package aarddict.android;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import aarddict.Dictionary;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TwoLineListItem;

public class LookupActivity extends Activity {
    
    final static String TAG     = "aarddict.LookupActivity";
    Timer               timer;
    ListView            listView;
    final Handler       handler = new Handler();
        
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        timer = new Timer();
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, 
                    LinearLayout.LayoutParams.FILL_PARENT, 1));
                     
        listView = new ListView(this);
        EditText editText = new EditText(this){
            
            TimerTask currentLookupTask;
            
            @Override
            public boolean onKeyUp(int keyCode, KeyEvent event) {
                if (currentLookupTask != null) {
                    currentLookupTask.cancel();
                }
                
                final CharSequence textToLookup = getText(); 
                
                currentLookupTask = new TimerTask() {                    
                    @Override
                    public void run() {
                        Log.d(TAG, "running lookup task for " + textToLookup + " in " + Thread.currentThread());
                        if (textToLookup.equals(getText())) {
                            doLookup(textToLookup);
                        }
                    }
                };                 
                timer.schedule(currentLookupTask, 600);                
                return true;
            }
        };
        editText.setHint("Start typing");
        editText.setInputType(InputType.TYPE_CLASS_TEXT | 
                              InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE |
                              InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                        
        layout.addView(editText);                        
        layout.addView(listView);        
        setContentView(layout);
        openDictionaries();
    }
    
    void openDictionaries() {
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage("Looking for dictionaries");
        Runnable open = new Runnable() {            
            @Override
            public void run() {
                while (!progressDialog.isShowing()) {
                    Thread.yield();
                    try {
                        Thread.sleep(10);
                    }
                    catch (InterruptedException e) {
                        Log.e(TAG, "Sombody interrupted my slumber", e);
                    }
                }
                Dictionaries.getInstance().discover();
                handler.post(new Runnable() {                    
                    @Override
                    public void run() {
                        progressDialog.cancel();
                    }
                });
            }
        };
        Thread t = new Thread(open);
        t.start();
        progressDialog.show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.cancel();
        Dictionaries.getInstance().close();
    }
    
    private void updateWordListUI(WordAdapter wordAdapter) {
        Log.d(TAG, "updating word list in " + Thread.currentThread());        
        int pos = listView.getLastVisiblePosition();        
        listView.setAdapter(wordAdapter);        
        listView.setOnItemClickListener(wordAdapter);
        listView.setSelection(pos-1);
    }    
    
    private void doLookup(CharSequence word) {        
        Iterator<Dictionary.Entry> results = Dictionaries.getInstance().lookup(word);
        final WordAdapter wordAdapter = new WordAdapter(results);
        final Runnable updateWordList = new Runnable() {            
            @Override
            public void run() {
                updateWordListUI(wordAdapter);
            }

        };        
        handler.post(updateWordList);
    }
    
    private void launchWord(Dictionary.Entry theWord) {
        Intent next = new Intent();
        next.setClass(this, ArticleViewActivity.class);                       
        next.putExtra("word", theWord.title);        
        next.putExtra("section", theWord.section);
        next.putExtra("dictionaryId", theWord.dictionary.getId());
        next.putExtra("articlePointer", theWord.articlePointer);
        startActivity(next);
    }
    
    
    class WordAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {

        private final List<Dictionary.Entry> words;
        private final LayoutInflater         mInflater;
        private int                          itemCount;
        private LinearLayout                 more;
        private Iterator<Dictionary.Entry>   results;
        private boolean                      displayMore;

        public WordAdapter(Iterator<Dictionary.Entry> results) {
            this.results = results;                        
            this.words = new ArrayList<Dictionary.Entry>();                        
            loadBatch();
            mInflater = (LayoutInflater) LookupActivity.this.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);

            more = new LinearLayout(mInflater.getContext());
            ImageView moreImage = new ImageView(mInflater.getContext());
            moreImage.setImageResource(android.R.drawable.ic_menu_more);
            more.setGravity(Gravity.CENTER);
            more.addView(moreImage);
        }

        private void loadBatch() {
            int count = 0;                
            while (results.hasNext() && count < 20) {
                count++;
                words.add(results.next());
            }                                        
            displayMore = results.hasNext();
            itemCount = displayMore  ? words.size() + 1 : words.size();              
        }
        
        public int getCount() {
            return itemCount;
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return displayMore ? 2 : 1;
        }
        
        @Override
        public int getItemViewType(int position) {
            if (displayMore)
                return position == itemCount - 1 ? 1 : 0;
            else
                return 0;
        }
        
        public View getView(int position, View convertView, ViewGroup parent) {
            if (displayMore && position == itemCount - 1) {
                return more;
            }
            TwoLineListItem view = (convertView != null) ? (TwoLineListItem) convertView :
                    createView(parent);            
            bindView(view, words.get(position));
            return view;
        }

        private TwoLineListItem createView(ViewGroup parent) {
            TwoLineListItem item = (TwoLineListItem) mInflater.inflate(
                    android.R.layout.simple_list_item_2, parent, false);
            item.getText2().setSingleLine();
            item.getText2().setEllipsize(TextUtils.TruncateAt.END);
            return item;
        }

        private void bindView(TwoLineListItem view, Dictionary.Entry word) {
            view.getText1().setText(word.title);
            view.getText2().setText(word.dictionary.getDisplayTitle());
        }

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (position == itemCount - 1) {
                Toast t = Toast.makeText(LookupActivity.this, "More", Toast.LENGTH_SHORT);
                t.show();
                loadBatch();
                updateWordListUI(this);
            }            
            else {
                launchWord(words.get(position));
            }
        }
    }
    
}
