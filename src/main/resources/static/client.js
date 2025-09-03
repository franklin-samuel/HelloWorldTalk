const LOCAL_IP_ADDRESS = "192.168.0.10";

let socket = io("https://f1b53ff69a6d.ngrok-free.app", {
  transports: ["websocket"],
  forceNew: true,
  secure: true
});
console.log("[Socket] Tentando conectar ao servidor Socket.IO...");
socket.on("connect", () => {
    console.log("[Socket] Conectado!");
})
socket.on("disconnect", () => {
    console.log("[Socket] Disconectado.")
})

let pc;
let localStream;
let remoteStream;
let room = null;
let role = null;

const getElement = id => document.getElementById(id);

const [btnConnect, stopBtn, nextBtn, localVideo, remoteVideo, remoteLoading, nativeLanguage, targetLanguage] = [
    "btnConnect", "stopBtn", "nextBtn", "localVideo", "remoteVideo", "remoteLoading", "nativeLanguage", "targetLanguage"
].map(getElement);

const iceServers = {
  iceServers: [
    { urls: "stun:stun.l.google.com:19302" },
    { urls: "stun:stun1.l.google.com:19302" },
    { urls: `turn:${LOCAL_IP_ADDRESS}:3478`, username: "userlocal", credential: 'senha123' }
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

    pc.ontrack = event => {
        remoteStream = event.streams[0];
        remoteVideo.srcObject = remoteStream;

        remoteLoading.style.display = "none";
    };

    pc.onicecandidate = event => {
        if (event.candidate) {
            socket.emit("candidate", { room, candidate: event.candidate });
        }
    };
}

socket.on("waiting", () => {
    console.log("aguardando parceiro...");
    remoteLoading.style.display = "block";
});

socket.on("match", async data => {
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
    if (pc.signalingState === "stable" || pc.signalingState === "have-remote-offer") {
        await pc.setRemoteDescription(new RTCSessionDescription(sdp));
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        socket.emit("answer", { room, sdp: answer });
    } else {
        console.warn("Ignorando offer, estado inválido:", pc.signalingState)
    }
});

socket.on("answer", async sdp => {
    if (pc && pc.signalingState === "have-local-offer") {
        await pc.setRemoteDescription(new RTCSessionDescription(sdp));
        socket.emit("ready", room);
    } else {
        console.warn("Ignorando answer, estado inválido:", pc?.signalingState);
    }
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
    document.getElementById("divRoomConfig").classList.remove("d-none");
    document.getElementById("roomDiv").classList.add("d-none");
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

btnConnect.onclick = async () => {
    const nativeLang = nativeLanguage.value;
    const targetLang = targetLanguage.value;

    if (!nativeLang || !targetLang) {
        alert("Selecione os idiomas antes de conectar!");
        return;
    } else if (nativeLang === targetLang) {
        alert("Os idiomas devem ser diferentes.");
    }



    console.log(`[Socket] Entrando na fila com idiomas: ${nativeLang} -> ${targetLang}`);
    await initMedia();
    socket.emit("joinQueue", { nativeLanguage: nativeLang, targetLanguage: targetLang });

    document.getElementById("divRoomConfig").classList.add("d-none");
    document.getElementById("roomDiv").classList.remove("d-none");
}

loadLanguages();