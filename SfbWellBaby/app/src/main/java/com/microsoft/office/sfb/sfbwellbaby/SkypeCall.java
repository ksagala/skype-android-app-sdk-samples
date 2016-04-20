package com.microsoft.office.sfb.sfbwellbaby;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.widget.ProgressBar;

import com.microsoft.office.sfb.appsdk.Conversation;
import com.microsoft.office.sfb.appsdk.SFBException;
import com.microsoft.office.sfb.sfbwellbaby.SkypeAPI.SkypeManager;
import com.microsoft.office.sfb.sfbwellbaby.SkypeAPI.SkypeManagerImpl;

import java.net.URI;
import java.net.URISyntaxException;

import butterknife.ButterKnife;


/**
 * SkypeCall activity hosts two fragments. The first fragment shows a progress indicator and is loaded
 * immediately after this activity is inflated. When the WaitForConnect fragment reports that it is
 * inflated, SkypeCall initiates the meeting join via SkypeManagerImpl.java. When SkypeManagerImpl
 * reports that the meeting is joined, SkypeCall removes the WaitForConnect fragment and replaces
 * it with the SkypeCallFragment. The SkypeCallFragment hosts the incoming video stream and a preview
 * of the outgoing stream.
 * SkypeCall finds the containing views in the SkypeCallFragment and provides those container views to
 * SkypeManagerImpl. SkypeManagerImpl sends the two video streams to the UI via those containers.
 */
public class SkypeCall extends AppCompatActivity
        implements SkypeManager.SkypeConversationJoinCallback, SkypeManager.SkypeVideoReady,
        SkypeCallFragment.OnFragmentInteractionListener {

    SkypeManagerImpl mSkypeManagerImpl;
    Conversation mAnonymousMeeting;
    SkypeCallFragment mCallFragment = null;
    FragmentManager mFragmentManager = null;
    private static final String VIDEO_FRAGMENT_STACK_STATE = "videoFragment";
    View mParticipantCallView;
    TextureView mPreviewCallView;


    ConversationPropertyChangeListener conversationPropertyChangeListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFragmentManager = getSupportFragmentManager();
        //Get the singleton instance of the skype manager
        mSkypeManagerImpl = SkypeManagerImpl.getInstance(
                getApplicationContext(),
                this, this);

        setContentView(R.layout.activity_skype_call);
        ButterKnife.inject(this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);
        startToJoinMeeting();

    }


    @Override
    protected void onPostResume() {
        super.onPostResume();



    }


    /**
     * Invoked by SkypeManagerImpl when the meeting is joined.
     *
     * @param conversation the newly joined / created Conversation
     *                     When joined, close the WaitForConnect fragment and add the
     *                     SkypeCallFragment
     */
    @Override
    public void onSkypeConversationJoinSuccess(Conversation conversation) {

        if (mParticipantCallView == null)
            return;

        try{
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
                    ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }

                  // View participantVideoContainer = mParticipantCallView.findViewById(R.id.participantVideoLayoutId);

                    //Get the containers for preview video and incoming video
                    //Set the preview video container
                    mSkypeManagerImpl.setCallView(mPreviewCallView);

                    //Set the incoming video container and start the video
                    mSkypeManagerImpl.startIncomingVideo(mParticipantCallView);

                    //Prepare the video preview
                    mSkypeManagerImpl.prepareOutgoingVideo();
                    mSkypeManagerImpl.startOutgoingVideo();
                }
            });
        } catch(Exception e){
            Log.e("SkypeCall", "exception on meeting started" );
        }


//        try {
//            mCallFragment = SkypeCallFragment.newInstance();
//
//
//            fragmentTransaction.add(
//                    R.id.fragment_container,
//                    mCallFragment,
//                    "video");
//            fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
//            fragmentTransaction.addToBackStack(VIDEO_FRAGMENT_STACK_STATE);
//            fragmentTransaction.commitAllowingStateLoss();
//            mAnonymousMeeting = conversation;
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }


    private void loadCallFragment(){
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        try {
            mCallFragment = SkypeCallFragment.newInstance();
            fragmentTransaction.add(
                    R.id.fragment_container,
                    mCallFragment,
                    "video");
            fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            fragmentTransaction.addToBackStack(VIDEO_FRAGMENT_STACK_STATE);
            fragmentTransaction.commitAllowingStateLoss();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * Invoked from SkypeCallFragment when inflated. Provides the TextureView for preview to the
     * SkypeManagerImpl
     *
     * @param callView
     * @param newMeetingURI
     */
    @Override
    public void onFragmentInteraction(View callView, String newMeetingURI) {
        try {
            if (newMeetingURI.contentEquals(getString(R.string.callFragmentInflated))) {
                mParticipantCallView = callView.findViewById(R.id.participantVideoLayoutId);

                mPreviewCallView = (TextureView) callView.findViewById(R.id.selfParticipantVideoView);
                mSkypeManagerImpl.setCallVideoReadyListener(mCallFragment);
                return;
            }
            if (newMeetingURI.contentEquals(getString(R.string.leaveCall))) {
                mFragmentManager.popBackStack(
                        VIDEO_FRAGMENT_STACK_STATE,
                        FragmentManager.POP_BACK_STACK_INCLUSIVE);
                leaveSkypeCall();
            }
            if (newMeetingURI.contentEquals(getString(R.string.pauseCall))) {
                mSkypeManagerImpl.stopStartOutgoingVideo();
            }
            if (newMeetingURI.contentEquals(getString(R.string.muteAudio))){
                mSkypeManagerImpl.stopStartOutgoingAudio();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void onSkypeConversationJoinFailure(SFBException ex) {
        finish();
    }

    /**
     * Connect to an existing Skype for Business meeting with the URI from
     * shared preferences. Normally the URI is supplied to the mobile device
     * via some mechanism outside the scope of this sample.
     */
    private void startToJoinMeeting() {
        SharedPreferences settings = getSharedPreferences(getString(R.string.meetingURIKey), 0);
        String meetingURIString = settings.getString(getString(R.string.meetingURIKey), "");
        URI meetingURI = null;
        try {
            meetingURI = new URI(meetingURIString);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        try {
            loadCallFragment();
            mSkypeManagerImpl.joinConversation(
                    meetingURI,
                    getString(R.string.fatherName)
            );

        } catch (SFBException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSkypeIncomingVideoReady() {

    }

    /**
     * The fragment TextureView that displays the preview is ready.
     * Start the outgoing video.
     */
    @Override
    public void onSkypeOutgoingVideoReady(boolean paused) {
        mSkypeManagerImpl.startOutgoingVideo();
    }

    @Override
    protected void onStop() {
        super.onStop();
        leaveSkypeCall();
    }

    private void leaveSkypeCall() {
        //If there is an active meeting and the user can leave then
        //leave the meeting before closing this activity
        try {
                mSkypeManagerImpl.leaveConversation();
        } catch (SFBException e) {
                e.printStackTrace();
        }

        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_skype_call, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()){
            case R.id.muteAudioMenuItem:
                mSkypeManagerImpl.stopStartOutgoingAudio();
                break;
            case R.id.pauseVideoMenuItem:
                mSkypeManagerImpl.stopStartOutgoingVideo();
                break;
        }

        return false;
    }
}
