
# HelloWorld

Um projeto criado para melhorar o aprendizado de outros idiomas. O sistema gera um match baseado em compatibilidade de escolhas de idiomas nativos e de aprendizado e cria uma sala de call de vídeo entre os dois.


## Demonstração

https://drive.google.com/file/d/1SQscsO3qJBLk3C0pKRMhj7aI8_RLC5oz/view?usp=sharing

## Arquitetura

Temos um WebSocket criado em Java que escuta eventos enviados pelo JavaScript e, a partir desses eventos, gerencia uma fila no Redis, adicionando, removendo, re-colocando e fazendo o match de clients.

1 - O client envia um evento joinQueue, o backend coloca ele na fila junto com os idiomas escolhidos e responde com o evento waiting.

2 - O backend, ao achar um parceiro compatível, remove os dois da fila e envia o evento match para ambos, junto com o ID da sala e o papel de cada um (caller e callee).

3 - O JavaScript, ao receber match, cria uma PeerConnection e captura a mídia (áudio e vídeo) de cada client. O algoritmo escolhe um dos clients para ser o caller, que então envia um evento offer + ID da sala.

4 - O backend repassa a offer para o outro client (callee), que responde criando uma answer e enviando-a de volta pelo WebSocket com o mesmo ID da sala.

5 - O backend, ao receber a answer, a encaminha para o caller, que define essa resposta como RemoteDescription na sua PeerConnection.

6 - Ambos os peers trocam ICE candidates através do WebSocket (candidate), permitindo que consigam atravessar NAT/firewall e estabelecer a conexão P2P.

7 - Quando ambos têm LocalDescription e RemoteDescription aplicados e a negociação de ICE é concluída, a mídia flui diretamente de um usuário para o outro via WebRTC.

8 - Eventos adicionais (stop, nextPartner, userDisconnected) permitem encerrar ou reiniciar a chamada. O backend, ao receber esses eventos, remove o usuário da sala e, se necessário, o recoloca na fila do Redis para encontrar um novo parceiro.
## Stack utilizada

**Front-end:** WebRTC, JavaScript

**Back-end:** Java 17+, Spring Boot, Spring Data Redis, Spring WebSocket

**Infra**: Redis, STUN/TURN.

