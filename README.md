# Distributed System with Practical Byzantine Fault Tolerance (PBFT)

Welcome to the **PBFT Distributed System** repository! This project implements the **Practical Byzantine Fault Tolerance (PBFT)** protocol, a widely used consensus algorithm that ensures reliable operation even in the presence of faulty or malicious nodes.

---

## 📝 **Overview**

This project demonstrates a fault-tolerant distributed system using PBFT to maintain consistency and reliability across nodes. The protocol is designed to handle **3f+1 nodes**, tolerating up to **f Byzantine faults**. It ensures safety, liveness, and consensus in adversarial conditions.

---

## ⚙️ **Features**

- **Fault Tolerance:** Handles up to `f` Byzantine faults in a `3f+1` node system.  
- **Secure Transactions:** Uses digital signatures to verify and validate messages.  
- **Message Phases:** Implements PBFT's pre-prepare, prepare, and commit phases.  
- **Efficient Communication:** Reduces communication overhead while maintaining consensus.  
- **Dynamic Configuration:** Nodes can join or leave while maintaining system integrity.  

---

## 🚀 **Getting Started**

### Prerequisites
- Java 17+
- Spring Boot
- Maven

### Setup Instructions
1. Clone this repository:  
   ```bash
   git clone https://github.com/MurtazaMister/Byzantine-Fault-Tolerance.git
   cd paxos
   ```
2. Build the project:
   ```bash
   mvn clean install
   ```
3. Run the application:
   ```bash
   run_servers.bat
   ```

---

## 🛠️ How It Works

1. **Node Communication:** Each node communicates with all others using sockets.  
2. **Transaction Flow:**  
   - A client sends a transaction request to a primary node.  
   - The primary initiates the **pre-prepare phase**, broadcasting the request.  
   - All nodes validate the request in the **prepare phase**.  
   - Nodes finalize consensus in the **commit phase**, ensuring the transaction is consistent across all replicas.  
3. **Fault Tolerance:**  
   - The system remains operational even if up to `f` nodes act maliciously or fail.  
   - Digital signatures and cryptographic hashes ensure secure message validation.  

---

## 🤝 Contributing

Contributions are welcome! Fork the repository, make improvements, and submit a pull request.

---

## 📧 Contact

For queries or feedback, reach out at:  
📬 **murtazaakil.mister@stonybrook.edu**
