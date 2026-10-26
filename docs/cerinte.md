# Cerințe

DistFS este un sistem de fișiere distribuit: un coordonator ține metadatele, nodurile de stocare țin octeții, iar fiecare fișier are 2 copii pe noduri diferite. Clientul vorbește cu coordonatorul pentru metadate și direct cu nodurile pentru date.

## 1. Operații

| Operație | Comanda în client | Ce face |
| --- | --- | --- |
| Alocare | `alloc <name> <size>` | Coordonatorul rezervă un ID pentru fișier și alege 2 noduri cu spațiu suficient. |
| Încărcare | `put <name> <path>` | Face `alloc`, trimite fișierul la primul nod, care îl replică la al doilea. |
| Descărcare | `get <id> <path>` | Află nodurile prin `LOCATE` și descarcă de la primul nod care răspunde. Verifică checksum-ul. |
| Localizare | `locate <name\|id>` | Întoarce metadatele fișierului și nodurile care îl dețin. |
| Listare | `list [limit] [offset]` | Listează fișierele confirmate, paginat (implicit 100, de la 0). |
| Ștergere | `del <name\|id>` | Șterge metadatele de la coordonator; nodurile șterg copiile. |
| Descoperire | `discover` | Listează nodurile cunoscute, cu stare și spațiu liber. |

## 2. Modelul metadatelor

### Fișier

| Câmp | Tip | Observații |
| --- | --- | --- |
| `id` | UUID v7 | Generat la `ALLOC`; ordonat după timp, ca ID-urile de nod. |
| `name` | string UTF-8 | Unic în sistem; fără spații și fără `/`. |
| `size` | long | Dimensiunea în octeți. |
| `checksum` | SHA-256, hex | Calculat de client înainte de `PUT`, verificat de fiecare nod și la `GET`. |
| `replicas` | listă de ID-uri de nod | Exact 2 noduri distincte. |
| `status` | `PENDING` / `COMMITTED` | `PENDING` după `ALLOC`, `COMMITTED` după `COMMIT`. |
| `createdAt` | timestamp | Momentul `ALLOC`. |

Un fișier `PENDING` care nu primește `COMMIT` în 60 s este șters. Doar fișierele `COMMITTED` apar la `LIST` și `LOCATE`.

### Nod 

| Câmp | Tip | Observații |
| --- | --- | --- |
| `id` | UUID v7 | Generat la pornirea nodului (`NodeInfo`). |
| `host`, `tcpPort` | string, int | Adresa la care nodul primește conexiuni TCP. |
| `role` | `NODE` / `COORDINATOR` | Momentan setat cu `--coordinator`. |
| `freeBytes` | long | Spațiul liber raportat în ultimul heartbeat. |
| `lastSeen` | timestamp | Momentul ultimului heartbeat primit. |
| `health` | `UP` / `DOWN` | `DOWN` dacă `lastSeen` e mai vechi de 6 s. |

### Stocare pe disc

Fiecare nod scrie fișierele în `data/<nodeId>/<fileId>`. Metadatele de la coordonator sunt ținute în memorie.

## 3. Cerințe nefuncționale

| Cerință | Obiectiv măsurabil | Cum se verifică |
| --- | --- | --- |
| Toleranță la defecte | `GET` reușește cu oricare un nod de stocare oprit. | În demo se oprește un nod care deține fișierul, apoi se face `get`. |
| Detectarea defectelor | Un nod oprit apare `DOWN` la `discover` în maxim 6 s. | Se cronometrează de la Ctrl+C pe nod până la schimbarea stării. |
| Integritate | 100% din fișierele descărcate au același SHA-256 ca originalul. | Clientul compară checksum-ul; se verifică și cu `sha256sum`. |
| Performanță la transfer | `put` + `get` pentru un fișier de 10 MB pe localhost în sub 2 s fiecare. | Se măsoară timpul comenzilor în client. |
| Performanță la metadate | `list` cu 1000 de fișiere răspunde în sub 200 ms. | Se încarcă 1000 de fișiere mici și se măsoară `list`. |
| Scalare | Minim 5 noduri pornite pe aceeași mașină, pe porturi diferite. | `scripts/start-local.sh` pornește 5 noduri. |
| Consistență | Un fișier apare la `LIST` doar după ce ambele copii sunt scrise. | `COMMIT` se trimite doar după `STORED` de la al doilea nod. |

**În afara scopului:** coordonatorul este punct unic de defect; dacă se oprește, metadatele se pierd. Nu există autentificare sau criptare.

## 4. Protocol

### TCP (client ↔ coordonator, client ↔ nod, nod ↔ nod)

Fiecare cerere este o linie de text terminată cu `\n`: numele mesajului, apoi câmpurile separate prin spațiu. La `PUT`, `REPLICATE` și răspunsul la `GET`, linia este urmată de exact `size` octeți. O conexiune transportă o cerere și un răspuns.

| Mesaj | De la → către | Conținut | Răspuns |
| --- | --- | --- | --- |
| ALLOC | client → coordonator | nume, dimensiune | NODES cu ID-ul fișierului și 2 noduri, sau ERROR |
| PUT | client → nod | ID, nume, dimensiune, checksum, nodul pentru copie + octeții fișierului | STORED sau ERROR |
| REPLICATE | nod → nod | ca PUT, fără nodul următor | STORED sau ERROR |
| COMMIT | nod → coordonator | ID fișier, nodurile care îl dețin | OK |
| LOCATE | client → coordonator | nume sau ID | LOCATIONS cu metadatele, sau NOT_FOUND |
| GET | client → nod | ID fișier | antet cu dimensiunea + octeții, sau NOT_FOUND |
| LIST | client → coordonator | limit (implicit 100), offset | LIST cu metadatele fișierelor, sau ERROR |
| DEL | client → coordonator | nume sau ID | OK sau NOT_FOUND |
| DISCOVER | client → coordonator | — | NODES cu ID, adresă, health, spațiu liber, sau ERROR |

Exemplu de încărcare:

```
client → coordonator   ALLOC poze.zip 10485760
coordonator → client   NODES <fileId> <idA> 127.0.0.1:7001 <idB> 127.0.0.1:7002
client → nod A         PUT <fileId> poze.zip 10485760 <sha256> 127.0.0.1:7002\n<octeți>
nod A → nod B          REPLICATE <fileId> poze.zip 10485760 <sha256>\n<octeți>
nod B → nod A          STORED
nod A → coordonator    COMMIT <fileId> <idA> <idB>
coordonator → nod A    OK
nod A → client         STORED
```

Dacă `REPLICATE` eșuează, nodul A răspunde clientului cu `ERROR` și nu trimite `COMMIT`.

### UDP multicast (nod → toate nodurile)

Toate nodurile, inclusiv coordonatorul, intră în grupul `239.255.42.99` pe portul dat cu `--udp-port`. Fiecare nod trimite la 2 s un heartbeat:

```
HEARTBEAT <nodeId> <host> <tcpPort> <role> <freeBytes>
```

Fiecare nod ascultă grupul și actualizează tabelul de noduri. Dacă nu sosesc 3 heartbeat-uri la rând (6 s), nodul este marcat `DOWN`. Coordonatorul răspunde la `DISCOVER` din acest tabel și alege la `ALLOC` doar noduri `UP` cu `freeBytes` cel puțin cât dimensiunea fișierului.

## 5. Scenariul demonstrației

1. Se pornesc coordonatorul și 3 noduri cu `scripts/start-local.sh`.
2. În client, `discover` arată cele 3 noduri `UP`.
3. `put raport.pdf ./raport.pdf` încarcă un fișier; `list` îl arată.
4. `locate raport.pdf` arată cele 2 noduri care îl dețin.
5. Se oprește cu Ctrl+C unul dintre aceste noduri; în maxim 6 s `discover` îl arată `DOWN`.
6. `get <id> ./copie.pdf` reușește de la nodul rămas; checksum-ul coincide.
7. `put` pentru un fișier nou alege doar noduri `UP`.
8. `del raport.pdf`; `list` nu îl mai arată, iar `locate` întoarce `NOT_FOUND`.
