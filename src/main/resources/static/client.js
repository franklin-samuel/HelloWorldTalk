const socket = io("https://7cc1d88909ae.ngrok-free.app", {
  transports: ["websocket"],
  forceNew: true
});

console.log("[Socket] Tentando conectar ao servidor Socket.IO...");

// DOM Elements
const getElement = id => document.getElementById(id);
const [
  btnConnect,
  btnToggleVideo,
  btnToggleAudio,
  btnNext,
  btnDisconnect,
  divRoomConfig,
  roomDiv,
  nativeLanguage,
  targetLanguage,
  localVideo,
  remoteVideo,
  statusIndicator
] = [
  "btnConnect",
  "toggleVideo",
  "toggleAudio",
  "btnNext",
  "btnDisconnect",
  "divRoomConfig",
  "roomDiv",
  "nativeLanguage",
  "targetLanguage",
  "localVideo",
  "remoteVideo",
  "statusIndicator"
].map(getElement);

// State variables
let localStream = null;
let remoteStream = null;
let rtcPeerConnection = null;
let isCaller = false;
let currentRoom = null;
let isConnected = false;
let currentLanguages = { native: '', target: '' };

// WebRTC configuration
const iceServers = {
  iceServers: [
    { urls: "stun:stun.l.google.com:19302" },
    { urls: "stun:stun1.l.google.com:19302" }
  ]
};
