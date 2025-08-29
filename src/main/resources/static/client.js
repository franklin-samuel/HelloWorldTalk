const socket = io("http://localhost:9092");
socket.on("connect", () => console.log("Conectado!"));
socket.on("disconnect", () => console.log("Desconectado!"));
console.log("[Socket] Tentando conectar ao servidor Socket.IO...");

const getElement = id => document.getElementById(id);
const [btnConnect, btnToggleVideo, btnToggleAudio, divRoomConfig, roomDiv, nativeLanguage, targetLanguage, localVideo, remoteVideo] =
  ["btnConnect", "toggleVideo", "toggleAudio", "divRoomConfig", "roomDiv", "nativeLanguage", "targetLanguage", "localVideo", "remoteVideo"]
  .map(getElement);

let localStream, remoteStream, rtcPeerConnection, isCaller;

const iceServers = { iceServers: [{ urls: "stun:stun.l.google.com:19302" }] };
const streamConstraints = { video: true, audio: true };

async function loadLanguages() {
  try {
    console.log("[Languages] Carregando idiomas do backend...");
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
    console.log("[Languages] Idiomas carregados com sucesso:", languages.map(l => l.code));
  } catch (err) {
    console.error("[Languages] Erro ao carregar idiomas:", err);
  }
}
loadLanguages();

btnConnect.onclick = async () => {
  const native = nativeLanguage.value;
  const target = targetLanguage.value;
  if (!native || !target) return alert("Selecione ambos os idiomas!");

  console.log("[Connect] Idiomas selecionados:", native, target);
  socket.emit("joinRoom", { nativeLanguage: native, targetLanguage: target });
  divRoomConfig.classList.add("d-none");
  roomDiv.classList.remove("d-none");

  try {
    console.log("[Media] Solicitando acesso a câmera e microfone...");
    localStream = await navigator.mediaDevices.getUserMedia(streamConstraints);
    localVideo.srcObject = localStream;
    console.log("[Media] Stream local obtida com sucesso.");
  } catch (err) {
    console.error("[Media] Erro ao obter stream local:", err);
  }
};

btnToggleVideo.onclick = () => toggleTrack("video");
btnToggleAudio.onclick = () => toggleTrack("audio");

function toggleTrack(type) {
  if (!localStream) return;
  const track = type === "video" ? localStream.getVideoTracks()[0] : localStream.getAudioTracks()[0];
  track.enabled = !track.enabled;
  console.log(`[Media] Track ${type} ${track.enabled ? "ativada" : "desativada"}`);
}

socket.on("joined", (room) => {
  console.log("[Socket] Entrou na sala:", room);
  isCaller = true;
  startPeerConnection();
});

socket.on("ready", () => {
  console.log("[Socket] Par pronto, criando offer...");
  if (isCaller) createOffer();
});

socket.on("offer", async (sdp) => {
  console.log("[Socket] Offer recebida:", sdp);
  if (!rtcPeerConnection) startPeerConnection();
  await rtcPeerConnection.setRemoteDescription(new RTCSessionDescription(sdp));
  const answer = await rtcPeerConnection.createAnswer();
  await rtcPeerConnection.setLocalDescription(answer);
  socket.emit("answer", answer);
  console.log("[WebRTC] Answer enviada.");
});

socket.on("answer", async (sdp) => {
  console.log("[Socket] Answer recebida:", sdp);
  await rtcPeerConnection.setRemoteDescription(new RTCSessionDescription(sdp));
});

socket.on("candidate", async (candidate) => {
  console.log("[Socket] Candidate recebido:", candidate);
  if (rtcPeerConnection && candidate) {
    try {
      await rtcPeerConnection.addIceCandidate(new RTCIceCandidate(candidate));
      console.log("[WebRTC] Candidate adicionado.");
    } catch (err) {
      console.error("[WebRTC] Erro ao adicionar candidate:", err);
    }
  }
});

function startPeerConnection() {
  console.log("[WebRTC] Criando RTCPeerConnection...");
  rtcPeerConnection = new RTCPeerConnection(iceServers);

  localStream.getTracks().forEach(track => {
    rtcPeerConnection.addTrack(track, localStream);
    console.log(`[WebRTC] Track adicionada: ${track.kind}`);
  });

  rtcPeerConnection.ontrack = (event) => {
    remoteVideo.srcObject = event.streams[0];
    remoteStream = event.streams[0];
    console.log("[WebRTC] Stream remota recebida:", event.streams[0]);
  };

  rtcPeerConnection.onicecandidate = (event) => {
    if (event.candidate) {
      socket.emit("candidate", event.candidate);
      console.log("[WebRTC] Candidate enviado:", event.candidate);
    }
  };

  rtcPeerConnection.onconnectionstatechange = () => {
    console.log("[WebRTC] Estado da conexão:", rtcPeerConnection.connectionState);
  };
}

async function createOffer() {
  const offer = await rtcPeerConnection.createOffer();
  await rtcPeerConnection.setLocalDescription(offer);
  socket.emit("offer", offer);
  console.log("[WebRTC] Offer criada e enviada:", offer);
}
