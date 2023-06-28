<script>
    export let instance;

    let name = instance.getName();
    let values = instance.get().toArray();
    let key = "NONE"
    function handleTextChange() {
        instance.set(kotlin.asList(...values));
    }

    function addInput(index){
        values.splice(index + 1, 0, "")
        values = values
    }

    function handleKeyPress(event, index) {
        
        if (event.which === 13) { // checks if enter is pressed
            addInput(index);
            const nextInput = event.target.nextSibling;
                if (nextInput) {
                    nextInput.focus();
                }
            handleTextChange()
        } else if (event.which === 8 && values[index] === "") {
            values.splice(index, 1)
            values = values
            handleTextChange()
        }
        
    }
</script>

<div class="setting">
    <div class="name">{name}</div>
    <div class="name">{key}</div>
    {#each values as value, index}
         <input type="text" bind:value={values[index]} on:change={handleTextChange} on:keydown={(e) => handleKeyPress(e, index)} placeholder={name} />
    {/each}
    

    
</div>

<style lang="scss">
    .setting {
        padding: 7px 10px;
    }

    .name {
        font-weight: 500;
        color: white;
        font-size: 12px;
    }

    input {
        width: 100%;
        background-color: rgba(0, 0, 0, 0.5);
        border: none;
        font-family: "Montserrat", sans-serif;
        padding: 5px;
        border-bottom: solid 2px transparent;
        border-radius: 5px;
        font-size: 12px;
        margin-top: 5px;
        color: white;
        transition: ease border-bottom 0.2s;
        border: solid 2px transparent;

        &:focus {
            border: solid 2px #4677ff;   
        }
    }
</style>
