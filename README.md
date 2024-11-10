# CSE 535: Distributed Systems
## Lab 2

### Objective
To design and implement a linear PBFT protocol on a distributed banking application with possible byzantine nodes.

### Implementation
- This project has been implemented in Java, Spring Boot
- All servers and clients are spawned from the same project
- All servers communicate via sockets
- Server-Client communication is facilitated via REST API's
- Servers occupy the ports {8081, 8082, 8083, 8084, 8085, 8086, 8087} for socket communication
- Servers occupy the ports {8091, 8092, 8093, 8094, 8095, 8096, 8097} for REST endpoints
- All the data is stored in a MySQL database
- The project is well-structured and applies coding practices and principles such as:
  - Separation of concerns
  - Dependency injections
  - Modular design
  - Consistent error handling
  - DRY principles (Don't repeat yourself)
  - Clean code practices with meaningful naming conventions

### Acknowledgements
- Use of [StackOverflow](stackoverflow.com) for solving commonly encountered errors
- Use of AI assistant tool - [ChatGPT](chat.openai.com) for
  - Syntax and format understanding and correction
  - Best practices to be followed for the flow of data within the system
  - To find out alternative and efficient methods for implementation of my logic
  - Debugging of errors
- Use of <b>IntelliJ's Built-in Smart Completion</b> code auto-completion feature to autofill repetitive parts of the code
