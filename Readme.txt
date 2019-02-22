-------------------------------------------------------------------------------
                                Tema 2 - APD
-------------------------------------------------------------------------------

AUTOR: Smadu Razvan-Alexandru  335CB

FISIERE INCLUSE:
  - CommunicationChannel.java
  - Miner.java
  - Readme.txt
  - Makefile

README
  Implementarea minerului
    Minerul ruleaza atata timp cat primeste mesaje care nu sunt de tipul EXIT.
    El ia un mesaje de la canalul de comunicatie, verifica daca informatia este 
    pentru el (adica sa ia un mesaj care nu e null sau pe care sa nu-l mai fi 
    rezolvat) si apoi il calculeaza rezumatul de cate ori i s-a spus cand a fost 
    instantiat. Dupa ce a terminat de hash-uit, creeaza un mesaj in care pune 
    camera curent, parintele si raspunsul si il trimite pe canal spre vrajitor.
    Minerul nu este responsabil de modul cum trimite sau primeste mesajele, sau
    oridinea lor.
  
  Implementarea canalului de comunicatie
    Canalul de comunicatie este responsabil de modul cum mesajele sunt primite 
    si transmise si asigura fluxul corect de date. De asemenea, este responsabil
    si de eficientizare traficului pentru a evita orice blocare/ingreunare a
    traficului de date. Pentru aceasta, exista doua zone in care mesajele sunt 
    puse si luate. Una in care vrajitorii pun si minerii iau (numit 
    wizardToMinerBuffer) si una in care minerii pun si vrajitorii iau (numit 
    minerToWizardBuffer). Fiecare buffer reprezinta locul unde producatorul isi 
    pune mesajul, iar consumatorul il ia si il prelucreaza. Un miner, respectiv
    un vrajitor este fie producator, fie consumator la un moment dat. Fiecare 
    buffer este impartit in mai multe cozi penru a creste eficienta de 
    trimitere/primire.

    Punerea mesajelor de catre mineri pe canalul de comunicatie
      Fiecare miner are o coada in care pune si este identificate de catre id-ul
      thread-ului acelui miner. In cazul in care minerul nu a mai pus vreun 
      mesaj, se va creea o noua coada (ArrayBlockingQueue). De asemenea exista 
      si un semafor folosit pentru a limita numarul de vrajitori care acceseaza
      buffer-ul minerilor. Cand apare o noua coada, se incrementeaza semaforul 
      care limiteaza numarul de vrajitori care acceseaza buffer-ul. Dupa ce 
      s-a creat coada (sau daca exista deja) se pune mesajul. 
    
    Luarea mesajelor de catre vrajitori de pe canalul de comunicatie
      Fiecare vrajitor se uita la care zona nu este folosita si ia 
      un mesaj din acea coada. In acest timp, niciun alt vrajitor nu mai poate 
      sa ia mesaje din acea coada, deci acestia vor cauta o alta coada 
      disponibila. Numarul de vrajitori care se uita la cozi este limitat de 
      semafor la numarul de cozi pe canalul de comunicatie. In cazul in care 
      coada este vida, vrajitorii nu se uita la ea.

    Punerea mesajelor de catre vrajitori pe canalul de comunicatie
      Vrajitorii pun informatia pe canal, impartita in mai multe mesaje (mai 
      precis doua mesaje). Prin urmare este responsabilitatea conalului de 
      comunicatie sa ii oblige pe vrajitori sa dea mesajele cum trebuie, in 
      oridine, pentru a evita coruperea informatiei, iar minerii sa prelucreze 
      date gresite. Astfel, pentru fiecare vrajitor exista o zona "temporara"
      in care se creeaza mesajul. Fiecare vrajitor stim ca va pune mesajele in 
      ordine, deci pe bufferul sa o sa puna, in 2 stagii, informatia. In primul 
      stagiu se creeaza un mesaj in care se pune nodul parinte in cazul in care 
      mesajul nu este de tip END (altfel se ignora mesajul pentru ca pe canal
      se asigura oricum ca mesajele nu vor fi duplicate in cazul in care 
      exista mai multi vrajitori care sa dea acelasi mesaj). In al doilea stagiu
      se pune restul informatiei in pachet si se trimite pe canalul de 
      comunicatie, urmand ca apoi sa se reseteza buffer-ul in care se creeaza 
      mesajul. Cand apare un nou vrajitor, se creeaza o noua coada si se 
      incrementeaza semaforul care limiteaza numarul de mineri la citire. Dupa 
      ce s-a pus mesajul in coada, se seteaza status-ul cozii ca fiind deblocat 
      (disponibil).

    Luarea mesajelor de catre mineri de pe canalul de comunicatie
      Mecanismul este identic cu cel pentru vrajitori. Fiecare miner se uita 
      la o coada si vede daca poate sa ia de acolo mesajul sau nu. Altfel se 
      uita la coada urmatoare si asa mai departe.
    
    Rolul semafoarelor in implementare
      Semafoarele sunt folosite pentru a limita numarul de consumatori (
      vrajitori sau mineri) care sa ia mesaje de pe canal. Acestia fac 
      modificari asupra canalului si pentru a orice problema de concurenta care
      poate aparea, se obliga ca numai o parte dintre mineri/vrajitori sa aiba 
      acces la acea zona de memorie. Cand se face release la semafor, se permite 
      urmatorului consumator sa isi ia mesajul.

    Rolul variabilelor de tip AtomicBoolean
      Acestea sunt folosite pentru a identifica cozile care sunt ocupate de 
      alte threaduri. Intrucat take() este un apel blocant, atunci cand 
      doua threaduri o sa ajunga acolo, ultimul il va astepta pe primul.
      Pentru a elimina acest timp de asteptare, threadul va accesa o alta 
      coada in cazul in care prima este ocupata.

    Rolul clasei Pair in implementare
      Clasa Pair este folosita doar pentru a putea face asocieri de tipul 
      (status_lock, queue).

    Functia contains()
      Aceasta functie verifica daca in hash-map exista un mesaj sau nu. 
      Verificarea se face pe fiecare coada. Acest lucru este pentru a nu
      pune minerii sa rezolve o camera de doua ori.

  Scalabilitatea implementarii
    Aceasta implementare este scalabila cu numarul de vrajitori, respectiv 
    numarul de mineri. Cu cat sunt mai multi mineri (si job-ul fiecarul miner 
    necesita mult timp de executie) se poate observa o reducere a timului de 
    executie, insa pana la un anumit numar de mineri. In acel caz apare un 
    bottleneck (cozi de asteptare al minerilor) atunci cand vor sa comunice cu 
    vrajitorul. Prin urmare, cresterea numarului de vrajitori va scadea timpul
    de executie al aplicatiei.
  
  Observatii:
    - Functiile encryptThisString() si encryptMultipleTimes() sunt luate si 
      scheletul temei, din solver.
    - Fiecare miner verifica daca a mai lucrat o camera in trecut, si ignora 
      mesajul in acest caz.
    - O implementare mai simpla care da aceleasi rezultate in general, este 
      folosirea unui lock diferit pentru fiecare functie, iar functiile de put 
      si get (mai putin putMessageWizardChannel()) sa aiba put()/take() care 
      sa fie inglobat de un synchronized(lock_i). Functia 
      putMessageWizardChannel() foloseste doar un singur Message pe post de 
      buffer, iar canalul de comunicatie este dat de doua ArrayBlockingQueue.
      Insa, implementarea submisa mi se pare ca poate obtine performante mai 
      bune pentru unele corner case-uri, castigand timp din accesarea mai 
      eficienta a canalului de comunicatie.
