from fastapi import FastAPI, BackgroundTasks
from pydantic import BaseModel

# Import our custom advanced modules
from cartoon_engine import cartoon_handler
from memory_engine import neural_memory
from agent_core import autonomous_agent

app = FastAPI(title="ORION AI - Unified Human Brain & Cognitive Cloud")

# Request Models
class CommandRequest(BaseModel):
    user_id: str
    command: str

class VideoRequest(BaseModel):
    user_id: str
    prompt: str
    num_scenes: int = 4

class MemoryRequest(BaseModel):
    user_id: str
    key: str
    value: str

# ---------------------------------------------------------
# 1. Main Cognitive Thought Endpoint (Human Brain + Memory + Agent)
# ---------------------------------------------------------
@app.post("/api/v1/brain/think")
async def process_brain_command(req: CommandRequest):
    # Execute autonomous agent logic
    execution_data = autonomous_agent.execute_task(req.command, req.user_id)
    
    # Automatically store interaction fact in neural memory
    neural_memory.store_fact(req.user_id, f"last_command_{req.user_id}", req.command)
    
    return {
        "status": "SUCCESS",
        "engine": "ORION-AI-COGNITIVE-CORE",
        "data": execution_data,
        "total_saved_memories": len(neural_memory.get_all_memories(req.user_id))
    }

# ---------------------------------------------------------
# 2. Long Cartoon Video Generation Endpoint
# ---------------------------------------------------------
def background_cartoon_pipeline(user_id: str, prompt: str):
    print(f"[Orion Cloud] Starting background cartoon generation for {user_id}...")
    scenes = cartoon_handler.generate_script(prompt)
    cartoon_handler.render_scenes(scenes)
    neural_memory.store_fact(user_id, "last_cartoon_prompt", prompt)

@app.post("/api/v1/brain/generate-cartoon")
async def trigger_cartoon(req: VideoRequest, background_tasks: BackgroundTasks):
    background_tasks.add_task(background_cartoon_pipeline, req.user_id, req.prompt)
    return {
        "status": "PROCESSING",
        "message": f"Orion Human Brain initiated multi-scene long cartoon pipeline for: '{req.prompt}'"
    }

# ---------------------------------------------------------
# 3. Neural Memory Management Endpoints
# ---------------------------------------------------------
@app.post("/api/v1/memory/store")
async def store_memory(req: MemoryRequest):
    neural_memory.store_fact(req.user_id, req.key, req.value)
    return {"status": "SUCCESS", "message": f"Saved fact [{req.key} -> {req.value}] into neural memory."}

@app.get("/api/v1/memory/get/{user_id}/{key}")
async def get_memory(user_id: str, key: str):
    val = neural_memory.get_fact(user_id, key)
    return {"status": "SUCCESS", "user_id": user_id, "key": key, "value": val}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
  
