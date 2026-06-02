import SwiftUI
import LoklokCore   // from the LoklokKit Swift Package (XCFramework)

// Point at your deployed Worker, or http://localhost:8787 with the iOS simulator.
private let baseURL = "http://localhost:8787"

struct ContentView: View {
    @State private var joined = false
    @State private var name = "Sara"
    @State private var room = "general"

    var body: some View {
        if joined {
            ChatView(name: name, room: room)
        } else {
            VStack(spacing: 16) {
                Text("Loklok").font(.largeTitle).bold()
                TextField("Your name", text: $name).textFieldStyle(.roundedBorder)
                TextField("Room", text: $room).textFieldStyle(.roundedBorder)
                Button("Join") { joined = true }
                    .buttonStyle(.borderedProminent)
                    .disabled(name.isEmpty || room.isEmpty)
            }
            .padding(32)
        }
    }
}

struct ChatView: View {
    let name: String
    let room: String

    @State private var chat: IosChannel?
    @State private var subs: [Cancellable] = []
    @State private var messages: [Message] = []
    @State private var connection = "connecting"
    @State private var typingUser: String?
    @State private var draft = ""

    private var selfUserId: String { "u_\(name.lowercased())" }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("Loklok").font(.headline)
                Spacer()
                Text(connection).font(.caption).foregroundStyle(.secondary)
            }.padding()

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 6) {
                        ForEach(messages, id: \.id) { msg in
                            bubble(msg).id(msg.id)
                        }
                    }.padding(.horizontal)
                }
                .onChange(of: messages.count) { _ in
                    if let last = messages.last { proxy.scrollTo(last.id, anchor: .bottom) }
                }
            }

            if let typingUser { Text("\(typingUser) is typing…").font(.caption2).foregroundStyle(.secondary) }

            HStack {
                TextField("Message", text: $draft)
                    .textFieldStyle(.roundedBorder)
                    .onChange(of: draft) { chat?.setTyping(isTyping: !$0.isEmpty) }
                Button("Send") {
                    let text = draft.trimmingCharacters(in: .whitespaces)
                    guard !text.isEmpty else { return }
                    draft = ""
                    chat?.send(text: text)
                    chat?.setTyping(isTyping: false)
                }
            }.padding()
        }
        .onAppear(perform: connect)
        .onDisappear { subs.forEach { $0.cancel() }; chat?.close() }
    }

    private func bubble(_ msg: Message) -> some View {
        let mine = msg.userId == selfUserId
        return HStack {
            if mine { Spacer() }
            VStack(alignment: .leading, spacing: 2) {
                if !mine { Text(msg.name).font(.caption).bold() }
                Text(msg.text)
                if mine && msg.status == .sending {
                    Text("sending…").font(.caption2).foregroundStyle(.secondary)
                }
            }
            .padding(10)
            .background(mine ? Color.blue.opacity(0.2) : Color.gray.opacity(0.2))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            if !mine { Spacer() }
        }
    }

    private func connect() {
        let token = DevToken.mint(userId: selfUserId, name: name)
        let client = ChatClientCompanion().connect(baseUrl: baseURL, token: token, userId: selfUserId, name: name)
        let ch = ChatClientKt.iosChannel(client, roomId: room)
        chat = ch
        subs = [
            ch.observeMessages { messages = $0 },
            ch.observeConnection { connection = "\($0)".lowercased() },
            ch.observeEvents { event in
                if let t = event as? ChatEvent.TypingChanged, t.userId != selfUserId {
                    typingUser = t.isTyping ? t.userId : nil
                }
            },
        ]
    }
}
