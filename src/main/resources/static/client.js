let socket = io("https://96e5629effef.ngrok-free.app", {
  transports: ["websocket"],
  forceNew: true,
  secure: true
});
console.log("[Socket] Tentando conectar ao servidor Socket.IO...");

let pc;
let localStream;
let remoteStream;
let room = null;
let role = null;

const getElement = id => document.getElementById(id);

const [btnConnect, btnToggleVideo, btnToggleAudio, localVideo, remoteVideo, nativeLanguage, targetLanguage] = [
    "btnConnect", "toggleVideo", "toggleAudio", "localVideo", "remoteVideo", "nativeLanguage", "targetLanguage"
].map(getElement);

const stopBtn = document.getElementById("stopBtn");
const nextBtn = document.getElementById("nextBtn");

const iceServers = {
  iceServers: [
    { urls: "stun:stun.l.google.com:19302" },
    { urls: "stun:stun1.l.google.com:19302" }
  ]
};

async function loadLanguages() {
    try {
        const response = await fetch("/languages");
        if (!response.ok) {
            throw new Error(`Erro ao carregar línguas: ${response.status}`);
        }
        const languages = await response.json();

        [nativeLanguage, targetLanguage].forEach(select => {
            select.innerHTML = "";
            languages.forEach(lang => {
                const option = document.createElement("option");
                option.value = lang.code;
                option.textContent = lang.name;
                select.appendChild(option);
            });
        });

        console.log("Línguas carregadas:", languages);
    } catch (err) {
        console.error("Falha ao buscar línguas:", err);
        return [];
    }
}

async function initMedia() {
    try {
        localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
        localVideo.srcObject = localStream;
    } catch (err) {
    console.error("Erro ao capturar mídia local:", err);
    }
}

function createPeerConnection() {
    pc = new RTCPeerConnection(iceServers);

    localStream.getTracks().forEach(track => pc.addTrack(track, localStream));

    remoteStream = new MediaStream();
    pc.ontrack = event => {
        event.streams[0].getTracks().forEach(track => remoteStream.addTrack(track));
        remoteVideo.srcObject = remoteStream;
    };

    pc.onicecandidate = event => {
        if (event.candidate) {
            socket.emit("candidate", { room, candidate: event.candidate });
        }
    };
}

socket.on("waiting", () => {
    console.log("aguardando parceiro...")
});

socket.on("match_found", async data => {
    room = data.room;
    role = data.role;
    console.log("Match encontrado:", room, role);

    createPeerConnection();

    if (role === "caller") {
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        socket.emit("offer", { room, sdp: offer });
    }
});

socket.on("offer", async sdp => {
    if (!pc) createPeerConnection();
    await pc.setRemoteDescription(new RTCSessionDescription(sdp));
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    socket.emit("answer", { room, sdp: answer });
});

socket.on("answer", async sdp => {
    await pc.setRemoteDescription(new RTCSessionDescription(sdp));
    socket.emit("ready", room);
});

socket.on("candidate", async payload => {
    if (pc) {
        try {
            await pc.addIceCandidate(new RTCIceCandidate(payload.candidate));
        } catch (err) {
            console.error("Erro ao adicionar ICE:", err);
        }
    }
});

socket.on("ready", () => {
    console.log("Ambos prontos, mídia deve fluir");
});

socket.on("userDisconnected", () => {
    console.log("Parceiro disconectou");
    cleanup();
});

socket.on("partnerNext", () => {
    console.log("Parceiro pulou para o próximo");
    cleanup();
});

socket.on("stopped", () => {
    console.log("Vôce parou a chamada");
    cleanup();
});

function cleanup() {
    if (pc) {
        pc.close();
        pc = null;
    }
    remoteVideo.srcObject = null;
    room = null;
    role = null;
}

stopBtn.onclick = () => socket.emit("stop");
nextBtn.onclick = () => socket.emit("nextPartner");

btnConnect.onclick = () => {
    const nativeLang = nativeLanguage.value;
    const targetLang = targetLanguage.value;

    if (!nativeLang || !targetLang) {
        alert("Selecione os idiomas antes de conectar!");
        return;
    }

    console.log(`[Socket] Entrando na fila com idiomas: ${nativeLang} -> ${targetLang}`);
    socket.emit("joinQueue", { nativeLanguage: nativeLang, targetLanguage: targetLang });

    document.getElementById("divRoomConfig").classList.add("d-none");
    document.getElementById("roomDiv").classList.remove("d-none");
}

loadLanguages();
initMedia();