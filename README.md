# Sisteme-Distribuite

DistFS: un sistem de fișiere distribuit. Un coordonator ține metadatele, nodurile de stocare țin fișierele (câte 2 copii pe noduri diferite), iar clientul trimite comenzi dintr-un terminal interactiv. Cerințele și protocolul sunt descrise în [docs/cerinte.md](docs/cerinte.md).

## Echipa

- Koma Kristian ([Madachii](https://github.com/Madachii))
- Hojda Paul ([ZetPlay10](https://github.com/ZetPlay10))
- Babuț Ștefan ([andimm09](https://github.com/andimm09))

## Cerințe

- JDK 23 sau mai nou (`java -version` trebuie să arate cel puțin 23)
- Maven nu trebuie instalat; proiectul vine cu Maven Wrapper (`mvnw`)

## Compilare

Linux / macOS / Git Bash:

```bash
./mvnw package -DskipTests
```

Windows (PowerShell / cmd):

```powershell
.\mvnw.cmd package -DskipTests
```

Rezultatul este `target/distfs.jar`, care conține toate dependențele.

## Pornire

Toate procesele pornesc din același jar.

### Nod de stocare

```bash
java -jar target/distfs.jar node --host localhost --tcp-port 7001 --udp-port 8000 --coordinator
java -jar target/distfs.jar node --host localhost --tcp-port 7002 --udp-port 8000
java -jar target/distfs.jar node --host localhost --tcp-port 7003 --udp-port 8000
```

| Opțiune | Obligatorie | Descriere |
| --- | --- | --- |
| `--host` | da | Adresa nodului; trebuie să fie adresa unei interfețe de rețea a mașinii. |
| `--tcp-port` | da | Portul pe care nodul primește conexiuni TCP; diferit pentru fiecare nod de pe aceeași mașină. |
| `--udp-port` | da | Portul UDP multicast; același pentru toate nodurile din cluster. |
| `--coordinator` | nu | Marchează nodul drept coordonator (unul singur în cluster). |

Nodul rulează până la Ctrl+C.

### Client

```bash
java -jar target/distfs.jar client
```

Clientul deschide un prompt `distfs>`. `help` listează comenzile, `quit` iese.

### Scripturi

Scripturile din `scripts/` compilează proiectul dacă jar-ul lipsește. Pe Windows se rulează din Git Bash.

```bash
scripts/start-local.sh    # coordonator + 2 noduri pe localhost (TCP 7001-7003, UDP 8000), loguri în data/logs/
scripts/start-client.sh   # clientul interactiv
```
