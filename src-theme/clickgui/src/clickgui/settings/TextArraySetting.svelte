<script>
    export let instance;

    let name = instance.getName();
    let values = instance.get();
    let hi = "hi"
    function handleTextChange(e) {
        instance.set(Java.to(values, "java.util.ArrayList"));
        hi = Java
        // instance.set(values)
    }
</script>

<div class="setting">
    <div class="name">{name}</div>
    <p>{hi}</p>
    <p>{Object.prototype.toString.apply(values)}</p>
    <!-- {#each values as value}
         <input type="text" bind:value={value} on:change={handleTextChange} placeholder={name} />
    {/each} -->
    

    
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
