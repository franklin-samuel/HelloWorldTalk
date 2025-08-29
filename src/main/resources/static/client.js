const LOCAL_IP_ADDRESS = "localhost";
const socket = io(`http://localhost:9092`);

const getElement = id => document.getElementById(id);
const [btnConnect, btnToggleVideo, btnToggleAudio, divRoomConfig, roomDiv, nativeLanguage, targetLanguage, localVideo, remoteVideo] =
  ["btnConnect", "toggleVideo", "toggleAudio", "roomConfig", "roomDiv", "nativeLanguage", "targetLanguage", "localVideo", "remoteVideo"]
  .map(getElement);

let localStream, remoteStream, rtcPeerConnection, isCaller;

const iceServers = { iceServers: [{ urls: "stun:stun.l.google.com:19302" }] };
const streamConstraints = { video: true, audio: true };

async function loadLanguages() {
  try {
    const res = await fetch("/languages");
    const languages = await res.json();
    languages.forEach(lang => {
      [nativeLanguage, targetLanguage].forEach(select => {
        const option = document.createElement("option");
        option.value = lang.code;
        option.textContent = lang.name;
        select.appendChild(option);
      });
    });
  } catch (err) {
    console.error("Erro ao carregar idiomas:", err);
  }
}
loadLanguages();

btnConnect.onclick = async () => {
  const native = nativeLanguage.value;
  const target = targetLanguage.value;
  if (!native || !target) return alert("Selecione ambos os idiomas!");

  socket.emit("joinRoom", { nativeLanguage: native, targetLanguage: target });
  divRoomConfig.classList.add("d-none");
  roomDiv.classList.remove("d-none");

  localStream = await navigator.mediaDevices.getUserMedia(streamConstraints);
  localVideo.srcObject = localStream;
};

btnToggleVideo.onclick = () => toggleTrack("video");
btnToggleAudio.onclick = () => toggleTrack("audio");

function toggleTrack(type) {
  if (!localStream) return;
  const track = type === "video" ? localStream.getVideoTracks()[0] : localStream.getAudioTracks()[0];
  track.enabled = !track.enabled;
  getElement(type === "video" ? "videoIcon" : "audioIcon").textContent = track.enabled ? (type === "video" ? "🎥" : "🎤") : "❌";
}

socket.on("joined", async () => {
  isCaller = true;
  startPeerConnection();
});

socket.on("ready", () => {
  if (isCaller) createOffer();
});

socket.on("offer", async (sdp) => {
  if (!rtcPeerConnection) startPeerConnection();
  await rtcPeerConnection.setRemoteDescription(new RTCSessionDescription(sdp));
  const answer = await rtcPeerConnection.createAnswer();
  await rtcPeerConnection.setLocalDescription(answer);
  socket.emit("answer", answer);
});

socket.on("answer", async (sdp) => {
  await rtcPeerConnection.setRemoteDescription(new RTCSessionDescription(sdp));
});

socket.on("candidate", async (candidate) => {
  if (rtcPeerConnection && candidate) {
    try {
      await rtcPeerConnection.addIceCandidate(new RTCIceCandidate(candidate));
    } catch (err) { console.error(err); }
  }
});

function startPeerConnection() {
  rtcPeerConnection = new RTCPeerConnection(iceServers);
  localStream.getTracks().forEach(track => rtcPeerConnection.addTrack(track, localStream));

  rtcPeerConnection.ontrack = (event) => {
    remoteVideo.srcObject = event.streams[0];
    remoteStream = event.streams[0];
  };

  rtcPeerConnection.onicecandidate = (event) => {
    if (event.candidate) socket.emit("candidate", event.candidate);
  };
}

async function createOffer() {
  const offer = await rtcPeerConnection.createOffer();
  await rtcPeerConnection.setLocalDescription(offer);
  socket.emit("offer", offer);
}
