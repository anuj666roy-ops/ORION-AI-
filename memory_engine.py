import json
import os

class OrionNeuralMemory:
    def __init__(self, db_path="orion_memory.json"):
        self.db_path = db_path
        self.load_memory()

    def load_memory(self):
        """Loads persistent brain memory from local storage file"""
        if os.path.exists(self.db_path):
            try:
                with open(self.db_path, "r") as f:
                    self.memory_store = json.load(f)
            except Exception:
                self.memory_store = {}
        else:
            self.memory_store = {}

    def save_memory(self):
        """Saves current state of human brain memory"""
        with open(self.db_path, "w") as f:
            json.dump(self.memory_store, f, indent=4)

    def store_fact(self, user_id: str, key: str, value: str):
        """Saves a specific fact or preference into neural long-term memory"""
        if user_id not in self.memory_store:
            self.memory_store[user_id] = {}
        self.memory_store[user_id][key] = value
        self.save_memory()
        print(f"[Neural Memory] Stored for {user_id}: {key} -> {value}")

    def get_fact(self, user_id: str, key: str) -> str:
        """Retrieves a specific fact from long-term memory"""
        user_data = self.memory_store.get(user_id, {})
        return user_data.get(key, "Memory trace not found.")

    def get_all_memories(self, user_id: str) -> dict:
        """Returns all remembered facts for the user"""
        return self.memory_store.get(user_id, {})

# Global instance for Orion AI Core
neural_memory = OrionNeuralMemory()
