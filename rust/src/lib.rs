use std::thread;

use jni::JNIEnv;
use jni::objects::{JClass, JString};

//librespot imports
use librespot_core::config::SessionConfig;
use librespot_core::session::Session;
use librespot_core::authentication::Credentials;
use librespot_discovery::DeviceType;
use librespot_playback::config::{PlayerConfig, Bitrate};
use librespot_playback::player::Player;
use librespot_playback::audio_backend;
use librespot_playback::mixer::NoOpVolume;

//helpers
use sha1::{Sha1, Digest};
use futures::stream::StreamExt; //required for discovery.next()
use tokio::runtime::Runtime;
use log::{info, error, LevelFilter};
use android_logger::Config;

static mut RUNTIME_HANDLE: Option<Runtime> = None;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_spotifyreceiver_NativeBridge_initLogger(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        Config::default().with_max_level(LevelFilter::Info).with_tag("RustSpotify"),
    );
    info!("Rust Logger Initialized");
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_spotifyreceiver_NativeBridge_startDevice(
    mut env: JNIEnv,
    _class: JClass,
    device_name_java: JString,
) {
    let device_name: String = env
        .get_string(&device_name_java)
        .expect("Couldn't get java string!")
        .into();

    info!("Starting Spotify Receiver: {}", device_name);

    thread::spawn(move || {
        let rt = Runtime::new().expect("Failed to create Tokio runtime");
        
        rt.block_on(async move {
            start_discovery_loop(device_name).await;
        });

        unsafe {
            RUNTIME_HANDLE = Some(rt);
        }
    });
}

async fn start_discovery_loop(device_name: String) {

    let mut hasher = Sha1::new();
    hasher.update(b"android_device_id"); 
    let device_id = hex::encode(hasher.finalize());

    let mut discovery = librespot_discovery::Discovery::builder(device_id.clone())
        .name(device_name)
        .device_type(DeviceType::Speaker)
        .launch()
        .expect("Failed to start Zeroconf discovery");

    info!("Discovery started. Waiting for connection...");

    loop {
        match discovery.next().await {
            Some(credentials) => {
                info!("Connection request received!");
                
                match connect_and_play(credentials).await {
                    Ok(_) => info!("Session finished cleanly"),
                    Err(e) => error!("Session error: {:?}", e),
                }
            }
            None => {
                error!("Discovery stream ended unexpectedly");
                break;
            }
        }
    }
}

async fn connect_and_play(credentials: Credentials) -> Result<(), Box<dyn std::error::Error>> {
    let session_config = SessionConfig::default();
    let (session, _) = Session::connect(session_config, credentials, None, false).await?;

    info!("Connected to Spotify! User: {}", session.username());

    let backend = audio_backend::find(None).expect("No audio backend found!");

    let player_config = PlayerConfig {
        bitrate: Bitrate::Bitrate320,
        ..PlayerConfig::default()
    };

    let (_player, mut event_channel) = Player::new(
        player_config,
        session.clone(),
        Box::new(NoOpVolume),
        move || backend(None, librespot_playback::config::AudioFormat::default())
    );

    info!("Player initialized.");

    while let Some(event) = event_channel.recv().await {
        info!("Player Event: {:?}", event);
    }
    
    Ok(())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_spotifyreceiver_NativeBridge_stopDevice(
    _env: JNIEnv,
    _class: JClass,
) {
    info!("Stopping device...");
}