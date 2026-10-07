int horizontalRadiusSquare = (int) ${input$radius_xz} - 1;
int verticalRadiusSquare = (int) ${input$radius_y} - 1;
int yIterationsSquare = verticalRadiusSquare;

for (int i = -yIterationsSquare; i <= yIterationsSquare; i++) {
    for (int xi = -horizontalRadiusSquare; xi <= horizontalRadiusSquare; xi++) {
        for (int zi = -horizontalRadiusSquare; zi <= horizontalRadiusSquare; zi++) {
            // Execute the desired statements within the square/cube
            ${statement$doinshape}
        }
    }
}
